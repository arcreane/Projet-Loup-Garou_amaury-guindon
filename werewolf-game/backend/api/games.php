<?php
// =====================================================
// Endpoints lobby + état + votes + actions
// Inclut : parties classées, ELO, chat, chasseur interactif
// La machine à états est dans game_logic.php
// =====================================================

function handle_list_games(): void {
    $rows = db()->query(
        "SELECT g.id, g.name, g.is_ranked, g.status, g.created_at,
                (SELECT COUNT(*) FROM session_players sp WHERE sp.session_id = g.id) AS nb_players
         FROM game_sessions g
         WHERE g.status = 'WAITING'
         ORDER BY g.created_at DESC"
    )->fetchAll();
    json_response(['games' => $rows]);
}

function handle_create_game(): void {
    $me = require_auth();
    $in = read_json();
    $name    = trim($in['name'] ?? ('Partie de ' . $me['pseudo']));
    $ranked  = !empty($in['is_ranked']) ? 1 : 0;

    $pdo = db();
    $pdo->beginTransaction();
    try {
        $pdo->prepare('INSERT INTO game_sessions(name, is_ranked, status, phase_duration) VALUES (?, ?, "WAITING", 60)')
            ->execute([$name, $ranked]);
        $sid = (int)$pdo->lastInsertId();
        $pdo->prepare('INSERT INTO session_players(session_id, player_id, is_host) VALUES (?,?,1)')
            ->execute([$sid, $me['id']]);
        $pdo->prepare('UPDATE players SET current_session_id = ? WHERE id = ?')->execute([$sid, $me['id']]);
        game_log($sid, $me['pseudo'] . ' a créé la partie' . ($ranked ? ' (classée)' : '') . '.');
        $pdo->commit();
        json_response(['session_id' => $sid]);
    } catch (Exception $e) {
        $pdo->rollBack();
        json_error('Création impossible : ' . $e->getMessage(), 500);
    }
}

function handle_join(int $sid): void {
    $me = require_auth();
    $g = db()->prepare('SELECT status FROM game_sessions WHERE id = ?');
    $g->execute([$sid]);
    $row = $g->fetch();
    if (!$row)                        json_error('Partie introuvable', 404);
    if ($row['status'] !== 'WAITING') json_error('Partie déjà lancée', 409);

    try {
        db()->prepare('INSERT INTO session_players(session_id, player_id) VALUES (?,?)')
            ->execute([$sid, $me['id']]);
        game_log($sid, $me['pseudo'] . ' a rejoint la partie.');
    } catch (PDOException $e) { /* déjà dedans */ }
    // Met à jour le statut "en partie"
    db()->prepare('UPDATE players SET current_session_id = ? WHERE id = ?')->execute([$sid, $me['id']]);
    json_response(['ok' => true]);
}

function handle_players(int $sid): void {
    $me = current_player();
    $stmt = db()->prepare(
        "SELECT p.id, p.pseudo, p.discriminator, p.avatar_url, p.elo, sp.is_alive, sp.is_host, sp.role
         FROM session_players sp
         JOIN players p ON p.id = sp.player_id
         WHERE sp.session_id = ?
         ORDER BY sp.joined_at"
    );
    $stmt->execute([$sid]);
    $players = $stmt->fetchAll();

    $session = db()->prepare('SELECT status FROM game_sessions WHERE id = ?');
    $session->execute([$sid]);
    $status = $session->fetchColumn();

    foreach ($players as &$p) {
        $reveal = ($status === 'ENDED') || (!$p['is_alive']) || ($me && $p['id'] == $me['id']);
        if (!$reveal) $p['role'] = null;
        $p['id']       = (int)$p['id'];
        $p['elo']      = (int)$p['elo'];
        $p['is_alive'] = (int)$p['is_alive'];
        $p['is_host']  = (int)$p['is_host'];
    }
    json_response(['players' => $players]);
}

function handle_state(int $sid): void {
    require_once __DIR__ . '/bots.php';

    $me = require_auth();

    // Faire jouer les bots puis avancer si phase résolue.
    // On boucle pour gérer les changements de phase en chaîne.
    for ($i = 0; $i < 6; $i++) {
        $before = phase_snapshot($sid);
        run_bots($sid);
        advance_if_needed($sid);
        $after = phase_snapshot($sid);
        if ($before === $after) break;
    }

    $g = db()->prepare('SELECT * FROM game_sessions WHERE id = ?');
    $g->execute([$sid]);
    $session = $g->fetch();
    if (!$session) json_error('Partie introuvable', 404);

    $stmt = db()->prepare(
        "SELECT p.id, p.pseudo, p.discriminator, p.avatar_url, p.elo, sp.is_alive, sp.is_host, sp.role, sp.elo_before, sp.elo_after
         FROM session_players sp JOIN players p ON p.id = sp.player_id
         WHERE sp.session_id = ? ORDER BY sp.joined_at"
    );
    $stmt->execute([$sid]);
    $players = $stmt->fetchAll();

    $myRole = null; $iAmDead = false;
    foreach ($players as $p) {
        if ($p['id'] == $me['id']) {
            $myRole = $p['role'];
            $iAmDead = !$p['is_alive'];
        }
    }

    foreach ($players as &$p) {
        // Les morts et les joueurs en fin de partie voient TOUS les rôles
        $reveal = ($session['status'] === 'ENDED') || (!$p['is_alive']) || ($p['id'] == $me['id']) || $iAmDead;
        if (!$reveal) $p['role'] = null;
        $p['id']         = (int)$p['id'];
        $p['elo']        = (int)$p['elo'];
        $p['is_alive']   = (int)$p['is_alive'];
        $p['is_host']    = (int)$p['is_host'];
        $p['elo_before'] = isset($p['elo_before']) ? (int)$p['elo_before'] : null;
        $p['elo_after']  = isset($p['elo_after'])  ? (int)$p['elo_after']  : null;
    }

    // Logs visibles
    $logStmt = db()->prepare(
        "SELECT message, visibility, target_player_id, created_at
         FROM game_logs
         WHERE session_id = ?
           AND ( visibility = 'ALL'
              OR (visibility = 'SELF' AND target_player_id = ?)
              OR (visibility = 'WEREWOLVES' AND ? = 'WEREWOLF') )
         ORDER BY created_at ASC, id ASC
         LIMIT 200"
    );
    $logStmt->execute([$sid, $me['id'], $myRole ?? '']);
    $logs = $logStmt->fetchAll();

    // Chat visible : ALL pour tous + WEREWOLVES uniquement pour loups (vivants OU morts qui étaient loups)
    $chatStmt = db()->prepare(
        "SELECT c.id, c.message, c.scope, c.created_at, p.pseudo, p.avatar_url
         FROM chat_messages c JOIN players p ON p.id = c.player_id
         WHERE c.session_id = ?
           AND ( c.scope = 'ALL' OR (c.scope = 'WEREWOLVES' AND ? = 'WEREWOLF') )
         ORDER BY c.created_at ASC, c.id ASC
         LIMIT 200"
    );
    $chatStmt->execute([$sid, $myRole ?? '']);
    $chat = $chatStmt->fetchAll();

    $started   = strtotime($session['phase_started_at']);
    $remaining = max(0, $session['phase_duration'] - (time() - $started));

    json_response([
        'session' => [
            'id'                => (int)$session['id'],
            'name'              => $session['name'],
            'is_ranked'         => (int)$session['is_ranked'],
            'status'            => $session['status'],
            'phase'             => $session['phase'],
            'round'             => (int)$session['round'],
            'phase_duration'    => (int)$session['phase_duration'],
            'timer'             => $remaining,
            'winner_team'       => $session['winner_team'],
            'pending_hunter_id' => $session['pending_hunter_id'] ? (int)$session['pending_hunter_id'] : null,
        ],
        'my_role' => $myRole,
        'i_am_dead' => $iAmDead,
        'players' => $players,
        'logs'    => $logs,
        'chat'    => $chat,
    ]);
}

function handle_start(int $sid): void {
    $me = require_auth();

    $h = db()->prepare('SELECT is_host FROM session_players WHERE session_id = ? AND player_id = ?');
    $h->execute([$sid, $me['id']]);
    if (!$h->fetchColumn()) json_error("Réservé à l'hôte", 403);

    $g = db()->prepare('SELECT status FROM game_sessions WHERE id = ?');
    $g->execute([$sid]);
    if ($g->fetchColumn() !== 'WAITING') json_error('Déjà lancée', 409);

    $ps = db()->prepare('SELECT player_id FROM session_players WHERE session_id = ?');
    $ps->execute([$sid]);
    $ids = array_column($ps->fetchAll(), 'player_id');
    if (count($ids) < 4) json_error('Au moins 4 joueurs requis', 422);

    assign_roles($sid, $ids);
    snapshot_elos($sid);

    db()->prepare("UPDATE game_sessions
                   SET status='NIGHT', phase='NIGHT_WEREWOLF', round=1,
                       phase_started_at=CURRENT_TIMESTAMP, phase_duration=45
                   WHERE id=?")->execute([$sid]);

    game_log($sid, 'La nuit tombe sur le village...');
    game_log($sid, 'Les loups-garous se réveillent et désignent leur victime.', 'WEREWOLVES');

    json_response(['ok' => true]);
}

function handle_vote(int $sid): void {
    $me = require_auth();
    $in = read_json();
    $target = (int)($in['target_id'] ?? 0);
    if ($target <= 0) json_error('target_id requis', 422);

    advance_if_needed($sid);

    $s = db()->prepare('SELECT status, phase, round FROM game_sessions WHERE id = ?');
    $s->execute([$sid]);
    $session = $s->fetch();
    if (!$session) json_error('Partie introuvable', 404);

    $sp = db()->prepare('SELECT role, is_alive FROM session_players WHERE session_id=? AND player_id=?');
    $sp->execute([$sid, $me['id']]);
    $row = $sp->fetch();
    if (!$row || !$row['is_alive']) json_error('Vous ne pouvez pas voter', 403);

    $phase = $session['phase'];
    if ($phase === 'NIGHT_WEREWOLF' && $row['role'] !== 'WEREWOLF') {
        json_error('Réservé aux loups la nuit', 403);
    }
    if (!in_array($phase, ['NIGHT_WEREWOLF','DAY_VOTE'], true)) {
        json_error('Phase ne permet pas le vote', 409);
    }

    try {
        db()->prepare('INSERT INTO votes(session_id,voter_id,target_id,phase,round) VALUES (?,?,?,?,?)')
            ->execute([$sid, $me['id'], $target, $phase, $session['round']]);
    } catch (PDOException $e) {
        db()->prepare('UPDATE votes SET target_id=? WHERE session_id=? AND voter_id=? AND phase=? AND round=?')
            ->execute([$target, $sid, $me['id'], $phase, $session['round']]);
    }

    advance_if_needed($sid);
    json_response(['ok' => true]);
}

function handle_votes(int $sid): void {
    require_auth();
    $s = db()->prepare('SELECT phase, round FROM game_sessions WHERE id=?');
    $s->execute([$sid]);
    $row = $s->fetch();
    if (!$row) json_error('Partie introuvable', 404);

    $v = db()->prepare(
        "SELECT v.target_id, p.pseudo AS target_pseudo, COUNT(*) AS nb
         FROM votes v JOIN players p ON p.id = v.target_id
         WHERE v.session_id=? AND v.phase=? AND v.round=?
         GROUP BY v.target_id, p.pseudo
         ORDER BY nb DESC"
    );
    $v->execute([$sid, $row['phase'], $row['round']]);
    json_response(['phase' => $row['phase'], 'round' => (int)$row['round'], 'tally' => $v->fetchAll()]);
}

function handle_action(int $sid): void {
    $me = require_auth();
    $in = read_json();
    $type   = $in['action_type'] ?? '';
    $target = isset($in['target_id']) ? (int)$in['target_id'] : null;

    advance_if_needed($sid);

    $s = db()->prepare('SELECT status, phase, round, pending_hunter_id FROM game_sessions WHERE id=?');
    $s->execute([$sid]);
    $session = $s->fetch();
    if (!$session) json_error('Partie introuvable', 404);

    $sp = db()->prepare('SELECT role, is_alive, witch_heal_used, witch_kill_used FROM session_players WHERE session_id=? AND player_id=?');
    $sp->execute([$sid, $me['id']]);
    $me_sp = $sp->fetch();
    if (!$me_sp) json_error('Action interdite', 403);

    $role  = $me_sp['role'];
    $phase = $session['phase'];
    $round = (int)$session['round'];

    switch ($type) {
        case 'SEER_REVEAL':
            if (!$me_sp['is_alive'] || $role !== 'SEER' || $phase !== 'NIGHT_SEER') json_error('Action invalide', 409);
            $tg = db()->prepare('SELECT p.pseudo, sp.role FROM session_players sp JOIN players p ON p.id=sp.player_id WHERE sp.session_id=? AND sp.player_id=?');
            $tg->execute([$sid, $target]);
            $t = $tg->fetch();
            if (!$t) json_error('Cible inconnue', 404);
            db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,target_id,round) VALUES (?,?,?,?,?)')
                ->execute([$sid, $me['id'], 'SEER_REVEAL', $target, $round]);
            game_log($sid, 'La Voyante a sondé ' . $t['pseudo'] . ' : il est ' . role_fr($t['role']) . '.', 'SELF', $me['id']);
            advance_if_needed($sid);
            json_response(['ok' => true, 'role' => $t['role'], 'pseudo' => $t['pseudo']]);
            break;

        case 'WITCH_HEAL':
            if (!$me_sp['is_alive'] || $role !== 'WITCH' || $phase !== 'NIGHT_WITCH') json_error('Action invalide', 409);
            if ($me_sp['witch_heal_used']) json_error('Potion de vie déjà utilisée', 409);
            db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,round) VALUES (?,?,?,?)')
                ->execute([$sid, $me['id'], 'WITCH_HEAL', $round]);
            db()->prepare('UPDATE session_players SET witch_heal_used=1 WHERE session_id=? AND player_id=?')
                ->execute([$sid, $me['id']]);
            advance_if_needed($sid);
            json_response(['ok' => true]);
            break;

        case 'WITCH_KILL':
            if (!$me_sp['is_alive'] || $role !== 'WITCH' || $phase !== 'NIGHT_WITCH') json_error('Action invalide', 409);
            if ($me_sp['witch_kill_used']) json_error('Potion de mort déjà utilisée', 409);
            if (!$target) json_error('target_id requis', 422);
            db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,target_id,round) VALUES (?,?,?,?,?)')
                ->execute([$sid, $me['id'], 'WITCH_KILL', $target, $round]);
            db()->prepare('UPDATE session_players SET witch_kill_used=1 WHERE session_id=? AND player_id=?')
                ->execute([$sid, $me['id']]);
            advance_if_needed($sid);
            json_response(['ok' => true]);
            break;

        case 'WITCH_PASS':
            if (!$me_sp['is_alive'] || $role !== 'WITCH' || $phase !== 'NIGHT_WITCH') json_error('Action invalide', 409);
            db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,round) VALUES (?,?,?,?)')
                ->execute([$sid, $me['id'], 'WITCH_HEAL', $round]);
            advance_if_needed($sid);
            json_response(['ok' => true]);
            break;

        case 'HUNTER_SHOT':
            // Le chasseur tire sa flèche après être mort. pending_hunter_id doit être lui.
            if ((int)$session['pending_hunter_id'] !== (int)$me['id']) json_error('Pas votre tour de tirer', 403);
            if (!$target) json_error('target_id requis', 422);
            db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,target_id,round) VALUES (?,?,?,?,?)')
                ->execute([$sid, $me['id'], 'HUNTER_SHOT', $target, $round]);
            db()->prepare('UPDATE game_sessions SET pending_hunter_id=NULL WHERE id=?')->execute([$sid]);
            kill_player($sid, $target, 'Abattu par le Chasseur', false);
            check_end($sid);
            advance_if_needed($sid);
            json_response(['ok' => true]);
            break;

        default:
            json_error('action_type inconnu', 422);
    }
}

// =====================================================
// CHAT
// =====================================================
function handle_chat_send(int $sid): void {
    $me = require_auth();
    $in = read_json();
    $msg = trim($in['message'] ?? '');
    $scope = ($in['scope'] ?? 'ALL') === 'WEREWOLVES' ? 'WEREWOLVES' : 'ALL';

    if ($msg === '') json_error('message vide', 422);
    if (mb_strlen($msg) > 240) $msg = mb_substr($msg, 0, 240);

    $sp = db()->prepare('SELECT role, is_alive FROM session_players WHERE session_id=? AND player_id=?');
    $sp->execute([$sid, $me['id']]);
    $row = $sp->fetch();
    if (!$row) json_error('Vous n\'êtes pas dans cette partie', 403);

    // Les morts ne parlent pas en jeu (mais peuvent en lobby)
    $gs = db()->prepare('SELECT status FROM game_sessions WHERE id=?');
    $gs->execute([$sid]);
    $status = $gs->fetchColumn();
    if (in_array($status, ['NIGHT','DAY'], true) && !$row['is_alive']) {
        json_error('Les morts ne parlent plus', 403);
    }
    if ($scope === 'WEREWOLVES' && $row['role'] !== 'WEREWOLF') {
        json_error('Canal réservé aux loups', 403);
    }
    db()->prepare('INSERT INTO chat_messages(session_id,player_id,message,scope) VALUES (?,?,?,?)')
        ->execute([$sid, $me['id'], $msg, $scope]);
    json_response(['ok' => true]);
}

// =====================================================
// HISTORIQUE & CLASSEMENT
// =====================================================
function handle_history(): void {
    $me = require_auth();
    $stmt = db()->prepare(
        "SELECT g.id, g.name, g.is_ranked, g.winner_team, g.created_at, g.ended_at,
                sp.role, sp.elo_before, sp.elo_after
         FROM session_players sp
         JOIN game_sessions g ON g.id = sp.session_id
         WHERE sp.player_id = ? AND g.status = 'ENDED'
         ORDER BY g.ended_at DESC
         LIMIT 50"
    );
    $stmt->execute([$me['id']]);
    json_response(['history' => $stmt->fetchAll()]);
}

function handle_leaderboard(): void {
    require_auth();
    $rows = db()->query(
        "SELECT id, pseudo, avatar_url, elo, games_played, games_won
         FROM players
         WHERE games_played > 0
         ORDER BY elo DESC
         LIMIT 50"
    )->fetchAll();
    json_response(['leaderboard' => $rows]);
}

function handle_me(): void {
    $me = require_auth();
    $stmt = db()->prepare('SELECT id, pseudo, discriminator, email, avatar_url, elo, games_played, games_won FROM players WHERE id=?');
    $stmt->execute([$me['id']]);
    json_response($stmt->fetch());
}

function handle_update_avatar(): void {
    $me = require_auth();
    $in = read_json();
    $avatar = trim($in['avatar_url'] ?? '');
    if (mb_strlen($avatar) > 64) $avatar = mb_substr($avatar, 0, 64);
    db()->prepare('UPDATE players SET avatar_url=? WHERE id=?')->execute([$avatar, $me['id']]);
    json_response(['ok' => true, 'avatar_url' => $avatar]);
}

// =====================================================
// MODE ENTRAÎNEMENT (vs bots IA)
// =====================================================
function handle_create_practice(): void {
    require_once __DIR__ . '/bots.php';
    require_once __DIR__ . '/game_logic.php';

    $me = require_auth();
    $in = read_json();
    $total = (int)($in['total_players'] ?? 5);
    if ($total < 4) $total = 4;
    if ($total > 10) $total = 10;
    $nbBots = $total - 1;

    // Sélectionne N bots au hasard
    $bs = db()->prepare('SELECT id FROM players WHERE is_bot = 1 ORDER BY RAND() LIMIT ' . (int)$nbBots);
    $bs->execute();
    $botIds = array_map('intval', array_column($bs->fetchAll(), 'id'));
    if (count($botIds) < $nbBots) {
        json_error('Pas assez de bots configurés dans la BDD', 500);
    }

    $pdo = db();
    $pdo->beginTransaction();
    try {
        $pdo->prepare('INSERT INTO game_sessions(name, is_ranked, status, phase_duration) VALUES (?, 0, "WAITING", 60)')
            ->execute(['Entraînement de ' . $me['pseudo']]);
        $sid = (int)$pdo->lastInsertId();

        // Hôte (le joueur humain)
        $pdo->prepare('INSERT INTO session_players(session_id, player_id, is_host) VALUES (?,?,1)')
            ->execute([$sid, (int)$me['id']]);
        $pdo->prepare('UPDATE players SET current_session_id = ? WHERE id = ?')
            ->execute([$sid, (int)$me['id']]);

        // Bots
        $stmt = $pdo->prepare('INSERT INTO session_players(session_id, player_id) VALUES (?,?)');
        foreach ($botIds as $bid) $stmt->execute([$sid, $bid]);

        game_log($sid, $me['pseudo'] . ' lance un entraînement contre ' . count($botIds) . ' bots.');
        $pdo->commit();

        // Démarrage immédiat
        $allIds = array_merge([(int)$me['id']], $botIds);
        assign_roles($sid, $allIds);
        snapshot_elos($sid);

        db()->prepare("UPDATE game_sessions
                       SET status='NIGHT', phase='NIGHT_WEREWOLF', round=1,
                           phase_started_at=CURRENT_TIMESTAMP, phase_duration=45
                       WHERE id=?")->execute([$sid]);

        game_log($sid, 'La nuit tombe sur le village...');
        game_log($sid, 'Les loups-garous se réveillent et désignent leur victime.', 'WEREWOLVES');

        // Premier tour des bots
        run_bots($sid);
        advance_if_needed($sid);
        run_bots($sid);
        advance_if_needed($sid);

        json_response(['session_id' => $sid]);
    } catch (Exception $e) {
        $pdo->rollBack();
        json_error('Erreur : ' . $e->getMessage(), 500);
    }
}

/** "phase|round|status" pour détecter une transition. */
function phase_snapshot(int $sid): string {
    $s = db()->prepare('SELECT phase, round, status FROM game_sessions WHERE id=?');
    $s->execute([$sid]);
    $r = $s->fetch();
    return ($r['phase'] ?? '') . '|' . ($r['round'] ?? '') . '|' . ($r['status'] ?? '');
}

function role_fr(?string $r): string {
    switch ($r) {
        case 'VILLAGER': return 'un simple Villageois';
        case 'WEREWOLF': return 'un LOUP-GAROU';
        case 'SEER':     return 'la Voyante';
        case 'WITCH':    return 'la Sorcière';
        case 'HUNTER':   return 'le Chasseur';
        default:         return 'inconnu';
    }
}
