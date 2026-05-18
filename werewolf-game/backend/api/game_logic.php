<?php
// =====================================================
// Logique de jeu : attribution rôles, machine à états,
// chasseur interactif, calcul ELO
// =====================================================

function assign_roles(int $sid, array $playerIds): void {
    $n = count($playerIds);
    $roles = [];
    $w = ($n >= 10) ? 3 : (($n >= 6) ? 2 : 1);
    for ($i = 0; $i < $w; $i++) $roles[] = 'WEREWOLF';
    if ($n >= 4) $roles[] = 'SEER';
    if ($n >= 5) $roles[] = 'WITCH';
    if ($n >= 7) $roles[] = 'HUNTER';
    while (count($roles) < $n) $roles[] = 'VILLAGER';
    shuffle($roles);

    $stmt = db()->prepare('UPDATE session_players SET role = ? WHERE session_id = ? AND player_id = ?');
    foreach ($playerIds as $i => $pid) {
        $stmt->execute([$roles[$i], $sid, $pid]);
    }
}

/** Sauvegarde l'ELO de chaque joueur au début de la partie (pour l'historique). */
function snapshot_elos(int $sid): void {
    $stmt = db()->prepare(
        "UPDATE session_players sp
         JOIN players p ON p.id = sp.player_id
         SET sp.elo_before = p.elo
         WHERE sp.session_id = ?"
    );
    $stmt->execute([$sid]);
}

/**
 * Avance la machine à états si nécessaire.
 * Si un Chasseur est en attente de tir, on bloque toute progression.
 */
function advance_if_needed(int $sid): void {
    $g = db()->prepare('SELECT * FROM game_sessions WHERE id = ?');
    $g->execute([$sid]);
    $s = $g->fetch();
    if (!$s || $s['status'] === 'ENDED' || $s['status'] === 'WAITING') return;

    // Tant que le Chasseur doit tirer, on attend (jusqu'à 30s)
    if (!empty($s['pending_hunter_id'])) {
        $hunterTimeout = (time() - strtotime($s['phase_started_at'])) >= 30;
        if (!$hunterTimeout) return;
        // Le chasseur n'a pas tiré → on libère et continue
        db()->prepare('UPDATE game_sessions SET pending_hunter_id=NULL WHERE id=?')->execute([$sid]);
        game_log($sid, 'Le Chasseur n\'a pas eu le temps de tirer.');
    }

    $phase = $s['phase'];
    $round = (int)$s['round'];
    $expired = (time() - strtotime($s['phase_started_at'])) >= (int)$s['phase_duration'];

    switch ($phase) {
        case 'NIGHT_WEREWOLF':
            $needed = count_alive_by_role($sid, 'WEREWOLF');
            $voted  = count_voters($sid, 'NIGHT_WEREWOLF', $round);
            if ($expired || ($needed > 0 && $voted >= $needed)) {
                go_to_phase($sid, 'NIGHT_SEER', 30);
                game_log($sid, 'Les loups se rendorment. La Voyante se réveille.', 'SELF', alive_player_id_by_role($sid, 'SEER'));
            }
            break;

        case 'NIGHT_SEER':
            $seer = alive_player_id_by_role($sid, 'SEER');
            $played = ($seer === null) || has_night_action($sid, $seer, 'SEER_REVEAL', $round);
            if ($expired || $played) {
                go_to_phase($sid, 'NIGHT_WITCH', 30);
                game_log($sid, 'La Voyante se rendort. La Sorcière se réveille.', 'SELF', alive_player_id_by_role($sid, 'WITCH'));
            }
            break;

        case 'NIGHT_WITCH':
            $witch = alive_player_id_by_role($sid, 'WITCH');
            $played = ($witch === null) ||
                      has_night_action($sid, $witch, 'WITCH_HEAL', $round) ||
                      has_night_action($sid, $witch, 'WITCH_KILL', $round);
            if ($expired || $played) {
                resolve_night($sid, $round);
                if (check_end($sid)) return;
                // Si un chasseur vient de mourir, ne pas avancer (attendre son tir)
                $g2 = db()->prepare('SELECT pending_hunter_id FROM game_sessions WHERE id=?');
                $g2->execute([$sid]);
                if ($g2->fetchColumn()) return;
                go_to_phase($sid, 'DAY_VOTE', 60, 'DAY');
                game_log($sid, 'Le jour se lève. Le village doit désigner un suspect.');
            }
            break;

        case 'DAY_VOTE':
            $needed = count_alive($sid);
            $voted  = count_voters($sid, 'DAY_VOTE', $round);
            if ($expired || ($needed > 0 && $voted >= $needed)) {
                resolve_day($sid, $round);
                if (check_end($sid)) return;
                $g2 = db()->prepare('SELECT pending_hunter_id FROM game_sessions WHERE id=?');
                $g2->execute([$sid]);
                if ($g2->fetchColumn()) return;
                db()->prepare('UPDATE game_sessions SET round = round + 1 WHERE id = ?')->execute([$sid]);
                go_to_phase($sid, 'NIGHT_WEREWOLF', 45, 'NIGHT');
                game_log($sid, 'La nuit retombe sur le village.');
            }
            break;
    }
}

// --- Helpers compteurs ---

function count_alive(int $sid): int {
    $s = db()->prepare('SELECT COUNT(*) FROM session_players WHERE session_id=? AND is_alive=1');
    $s->execute([$sid]);
    return (int)$s->fetchColumn();
}
function count_alive_by_role(int $sid, string $role): int {
    $s = db()->prepare('SELECT COUNT(*) FROM session_players WHERE session_id=? AND is_alive=1 AND role=?');
    $s->execute([$sid, $role]);
    return (int)$s->fetchColumn();
}
function alive_player_id_by_role(int $sid, string $role): ?int {
    $s = db()->prepare('SELECT player_id FROM session_players WHERE session_id=? AND is_alive=1 AND role=? LIMIT 1');
    $s->execute([$sid, $role]);
    $v = $s->fetchColumn();
    return $v ? (int)$v : null;
}
function count_voters(int $sid, string $phase, int $round): int {
    $s = db()->prepare('SELECT COUNT(*) FROM votes WHERE session_id=? AND phase=? AND round=?');
    $s->execute([$sid, $phase, $round]);
    return (int)$s->fetchColumn();
}
function has_night_action(int $sid, int $pid, string $type, int $round): bool {
    $s = db()->prepare('SELECT 1 FROM night_actions WHERE session_id=? AND player_id=? AND action_type=? AND round=?');
    $s->execute([$sid, $pid, $type, $round]);
    return (bool)$s->fetchColumn();
}

function majority_target(int $sid, string $phase, int $round): ?int {
    $s = db()->prepare(
        "SELECT target_id, COUNT(*) c
         FROM votes WHERE session_id=? AND phase=? AND round=?
         GROUP BY target_id ORDER BY c DESC"
    );
    $s->execute([$sid, $phase, $round]);
    $rows = $s->fetchAll();
    if (!$rows) return null;
    if (count($rows) > 1 && $rows[0]['c'] === $rows[1]['c']) return null;
    return (int)$rows[0]['target_id'];
}

// --- Résolutions ---

function resolve_night(int $sid, int $round): void {
    $wolfTarget = majority_target($sid, 'NIGHT_WEREWOLF', $round);

    $sw = db()->prepare("SELECT action_type, target_id FROM night_actions
                         WHERE session_id=? AND round=? AND action_type IN ('WITCH_HEAL','WITCH_KILL')");
    $sw->execute([$sid, $round]);
    $heal = false; $kill = null;
    foreach ($sw->fetchAll() as $a) {
        if ($a['action_type'] === 'WITCH_HEAL') $heal = true;
        if ($a['action_type'] === 'WITCH_KILL') $kill = (int)$a['target_id'];
    }

    $deaths = [];
    if ($wolfTarget && !$heal) $deaths[] = $wolfTarget;
    if ($kill)                  $deaths[] = $kill;

    foreach (array_unique($deaths) as $pid) {
        kill_player($sid, $pid, 'Tué pendant la nuit');
    }
    if (empty($deaths)) {
        game_log($sid, 'Aucune victime cette nuit. Étrange...');
    }
}

function resolve_day(int $sid, int $round): void {
    $target = majority_target($sid, 'DAY_VOTE', $round);
    if (!$target) {
        game_log($sid, 'Le village n\'a pas réussi à se mettre d\'accord. Personne n\'est éliminé.');
        return;
    }
    kill_player($sid, $target, 'Éliminé par le vote du village');
}

/**
 * Tue un joueur. Si c'est le Chasseur, ouvre une fenêtre de tir interactive
 * (pending_hunter_id) que le client devra résoudre via POST /action HUNTER_SHOT.
 *
 * $allowHunterTrigger : faux quand on est déjà DANS la résolution d'un tir
 * de chasseur (évite la boucle infinie chasseur tue chasseur).
 */
function kill_player(int $sid, int $pid, string $reason, bool $allowHunterTrigger = true): void {
    $st = db()->prepare('SELECT p.pseudo, sp.role, sp.is_alive FROM session_players sp JOIN players p ON p.id=sp.player_id WHERE sp.session_id=? AND sp.player_id=?');
    $st->execute([$sid, $pid]);
    $row = $st->fetch();
    if (!$row || !$row['is_alive']) return;

    db()->prepare('UPDATE session_players SET is_alive=0 WHERE session_id=? AND player_id=?')->execute([$sid, $pid]);
    game_log($sid, $row['pseudo'] . ' est mort. ' . $reason . '. Il était ' . role_fr($row['role']) . '.');

    if ($row['role'] === 'HUNTER' && $allowHunterTrigger) {
        // Ouvre la fenêtre de tir : le client du chasseur recevra pending_hunter_id et affichera un dialog
        db()->prepare('UPDATE game_sessions SET pending_hunter_id=?, phase_started_at=CURRENT_TIMESTAMP WHERE id=?')
            ->execute([$pid, $sid]);
        game_log($sid, $row['pseudo'] . ' (Chasseur) va décocher une dernière flèche...');
    }
}

function check_end(int $sid): bool {
    $wolves    = count_alive_by_role($sid, 'WEREWOLF');
    $villagers = count_alive($sid) - $wolves;

    if ($wolves === 0) {
        end_game($sid, 'VILLAGERS');
        return true;
    }
    if ($wolves >= $villagers) {
        end_game($sid, 'WEREWOLVES');
        return true;
    }
    return false;
}

function end_game(int $sid, string $winner): void {
    db()->prepare("UPDATE game_sessions SET status='ENDED', phase='ENDED', winner_team=?, ended_at=CURRENT_TIMESTAMP WHERE id=?")
        ->execute([$winner, $sid]);
    $msg = ($winner === 'VILLAGERS')
        ? 'Les Villageois ont gagné ! Le village est sauvé.'
        : 'Les Loups-Garous ont gagné ! Le village est dévoré.';
    game_log($sid, $msg);

    // Mise à jour ELO si partie classée
    $gs = db()->prepare('SELECT is_ranked FROM game_sessions WHERE id=?');
    $gs->execute([$sid]);
    if ((int)$gs->fetchColumn() === 1) {
        apply_elo_changes($sid, $winner);
    } else {
        // Met quand même à jour games_played/won
        update_stats_only($sid, $winner);
    }
}

function go_to_phase(int $sid, string $phase, int $duration, ?string $status = null): void {
    if ($status) {
        db()->prepare("UPDATE game_sessions SET status=?, phase=?, phase_duration=?, phase_started_at=CURRENT_TIMESTAMP WHERE id=?")
            ->execute([$status, $phase, $duration, $sid]);
    } else {
        db()->prepare("UPDATE game_sessions SET phase=?, phase_duration=?, phase_started_at=CURRENT_TIMESTAMP WHERE id=?")
            ->execute([$phase, $duration, $sid]);
    }
}

// =====================================================
// ELO : formule classique K=32, par équipe (Loups vs Village)
// =====================================================
function apply_elo_changes(int $sid, string $winnerTeam): void {
    $stmt = db()->prepare(
        "SELECT sp.player_id, sp.role, p.elo
         FROM session_players sp JOIN players p ON p.id = sp.player_id
         WHERE sp.session_id = ?"
    );
    $stmt->execute([$sid]);
    $rows = $stmt->fetchAll();

    $wolves = []; $vill = [];
    foreach ($rows as $r) {
        if ($r['role'] === 'WEREWOLF') $wolves[] = $r;
        else                            $vill[]   = $r;
    }
    if (!$wolves || !$vill) return;

    $avgWolves = array_sum(array_column($wolves, 'elo')) / count($wolves);
    $avgVill   = array_sum(array_column($vill,   'elo')) / count($vill);

    $K = 32;
    $expWolves = 1.0 / (1 + pow(10, ($avgVill - $avgWolves) / 400));
    $expVill   = 1.0 - $expWolves;

    $wonWolves = ($winnerTeam === 'WEREWOLVES') ? 1 : 0;
    $wonVill   = 1 - $wonWolves;

    $deltaWolves = (int)round($K * ($wonWolves - $expWolves));
    $deltaVill   = (int)round($K * ($wonVill   - $expVill));

    apply_delta($sid, $wolves, $deltaWolves, $wonWolves);
    apply_delta($sid, $vill,   $deltaVill,   $wonVill);

    // log lisible
    $sign = $deltaWolves >= 0 ? '+' : '';
    game_log($sid, "ELO classé — Loups : {$sign}{$deltaWolves}, Village : " . ($deltaVill >= 0 ? '+' : '') . $deltaVill);
}

function apply_delta(int $sid, array $players, int $delta, int $won): void {
    $upd = db()->prepare(
        "UPDATE players p
         JOIN session_players sp ON sp.player_id = p.id AND sp.session_id = ?
         SET p.elo = p.elo + ?,
             p.games_played = p.games_played + 1,
             p.games_won = p.games_won + ?,
             sp.elo_after = p.elo + ?
         WHERE p.id = ?"
    );
    foreach ($players as $pl) {
        $upd->execute([$sid, $delta, $won, $delta, $pl['player_id']]);
    }
}

function update_stats_only(int $sid, string $winnerTeam): void {
    $stmt = db()->prepare(
        "SELECT sp.player_id, sp.role
         FROM session_players sp WHERE sp.session_id = ?"
    );
    $stmt->execute([$sid]);
    foreach ($stmt->fetchAll() as $r) {
        $won = ((($r['role'] === 'WEREWOLF') && $winnerTeam === 'WEREWOLVES') ||
                (($r['role'] !== 'WEREWOLF') && $winnerTeam === 'VILLAGERS')) ? 1 : 0;
        db()->prepare('UPDATE players SET games_played = games_played + 1, games_won = games_won + ? WHERE id = ?')
            ->execute([$won, $r['player_id']]);
        // En non classé : pas de modif ELO mais on remplit quand même elo_after pour cohérence
        db()->prepare('UPDATE session_players SET elo_after = elo_before WHERE session_id=? AND player_id=?')
            ->execute([$sid, $r['player_id']]);
    }
}
