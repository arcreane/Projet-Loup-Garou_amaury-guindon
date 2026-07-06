<?php
// =====================================================
// IA des bots pour le mode entraînement
//
// Architecture : run_bots() est appelée à chaque transition
// de phase. Elle identifie tous les bots vivants et leur
// fait jouer leur action si la phase courante les concerne.
// =====================================================

/**
 * Fait jouer tous les bots vivants de la session pour la phase courante.
 * Idempotent : si un bot a déjà joué ce round, on ne refait pas.
 */
function run_bots(int $sid): void {
    $stmt = db()->prepare('SELECT status, phase, round, pending_hunter_id FROM game_sessions WHERE id=?');
    $stmt->execute([$sid]);
    $session = $stmt->fetch();
    if (!$session) return;
    if (in_array($session['status'], ['WAITING','ENDED'], true)) return;

    $phase = $session['phase'];
    $round = (int)$session['round'];
    $pendingHunter = $session['pending_hunter_id'] ? (int)$session['pending_hunter_id'] : null;

    // Bots vivants
    $bots = db()->prepare(
        "SELECT p.id, p.pseudo, sp.role, sp.witch_heal_used, sp.witch_kill_used
         FROM session_players sp JOIN players p ON p.id = sp.player_id
         WHERE sp.session_id = ? AND p.is_bot = 1 AND sp.is_alive = 1"
    );
    $bots->execute([$sid]);
    foreach ($bots->fetchAll() as $bot) {
        bot_act($sid, $bot, $phase, $round);
    }

    // Si le bot Chasseur doit tirer
    if ($pendingHunter) {
        $h = db()->prepare(
            "SELECT p.id, p.pseudo FROM players p
             WHERE p.id = ? AND p.is_bot = 1");
        $h->execute([$pendingHunter]);
        $hunter = $h->fetch();
        if ($hunter) bot_hunter_shoot($sid, (int)$hunter['id'], $round);
    }
}

function bot_act(int $sid, array $bot, string $phase, int $round): void {
    $bid  = (int)$bot['id'];
    $role = $bot['role'];

    switch ($phase) {
        case 'NIGHT_WEREWOLF':
            if ($role !== 'WEREWOLF') return;
            if (bot_has_voted($sid, $bid, 'NIGHT_WEREWOLF', $round)) return;
            $target = bot_pick_target_for_wolf($sid);
            if ($target) {
                db()->prepare('INSERT INTO votes(session_id,voter_id,target_id,phase,round) VALUES (?,?,?,?,?)')
                    ->execute([$sid, $bid, $target, 'NIGHT_WEREWOLF', $round]);
                bot_chat_maybe($sid, $bid, 'WEREWOLVES', 'wolf_vote');
            }
            break;

        case 'NIGHT_SEER':
            if ($role !== 'SEER') return;
            if (bot_has_night_action($sid, $bid, 'SEER_REVEAL', $round)) return;
            $target = bot_pick_random_alive_except($sid, $bid);
            if ($target) {
                db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,target_id,round) VALUES (?,?,?,?,?)')
                    ->execute([$sid, $bid, 'SEER_REVEAL', $target, $round]);
            }
            break;

        case 'NIGHT_WITCH':
            if ($role !== 'WITCH') return;
            if (bot_has_night_action($sid, $bid, 'WITCH_HEAL', $round) ||
                bot_has_night_action($sid, $bid, 'WITCH_KILL', $round) ||
                bot_has_night_action($sid, $bid, 'WITCH_PASS', $round)) return;

            // Probabilité de soigner si on a la potion : 50%, sinon pass
            $healAvailable = !((int)$bot['witch_heal_used']);
            $killAvailable = !((int)$bot['witch_kill_used']);
            $r = mt_rand(0, 99);

            if ($healAvailable && $r < 50) {
                db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,round) VALUES (?,?,?,?)')
                    ->execute([$sid, $bid, 'WITCH_HEAL', $round]);
                db()->prepare('UPDATE session_players SET witch_heal_used=1 WHERE session_id=? AND player_id=?')
                    ->execute([$sid, $bid]);
            } elseif ($killAvailable && $r < 60) {
                $target = bot_pick_random_alive_except($sid, $bid);
                if ($target) {
                    db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,target_id,round) VALUES (?,?,?,?,?)')
                        ->execute([$sid, $bid, 'WITCH_KILL', $target, $round]);
                    db()->prepare('UPDATE session_players SET witch_kill_used=1 WHERE session_id=? AND player_id=?')
                        ->execute([$sid, $bid]);
                }
            } else {
                // Pass : marqueur dédié (un faux WITCH_HEAL annulerait l'attaque des loups)
                db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,round) VALUES (?,?,?,?)')
                    ->execute([$sid, $bid, 'WITCH_PASS', $round]);
            }
            break;

        case 'DAY_VOTE':
            if (bot_has_voted($sid, $bid, 'DAY_VOTE', $round)) return;
            $target = bot_pick_target_for_day_vote($sid, $bid, $role);
            if ($target) {
                db()->prepare('INSERT INTO votes(session_id,voter_id,target_id,phase,round) VALUES (?,?,?,?,?)')
                    ->execute([$sid, $bid, $target, 'DAY_VOTE', $round]);
                bot_chat_maybe($sid, $bid, 'ALL', 'day_vote');
            }
            break;
    }
}

function bot_hunter_shoot(int $sid, int $bid, int $round): void {
    $target = bot_pick_random_alive_except($sid, $bid);
    if (!$target) {
        db()->prepare('UPDATE game_sessions SET pending_hunter_id=NULL WHERE id=?')->execute([$sid]);
        return;
    }
    db()->prepare('INSERT INTO night_actions(session_id,player_id,action_type,target_id,round) VALUES (?,?,?,?,?)')
        ->execute([$sid, $bid, 'HUNTER_SHOT', $target, $round]);
    db()->prepare('UPDATE game_sessions SET pending_hunter_id=NULL WHERE id=?')->execute([$sid]);
    kill_player($sid, $target, 'Abattu par le Chasseur', false);
    check_end($sid);
}

// ----- Sélection de cibles -----

/**
 * Pour un loup : pick a random alive non-werewolf.
 * Préfère la victime déjà votée par d'autres loups (effet meute).
 */
function bot_pick_target_for_wolf(int $sid): ?int {
    // Cherche les votes déjà existants ce round par d'autres loups
    $s = db()->prepare("SELECT g.round FROM game_sessions g WHERE g.id=?");
    $s->execute([$sid]);
    $round = (int)$s->fetchColumn();

    $v = db()->prepare(
        "SELECT target_id, COUNT(*) c FROM votes
         WHERE session_id=? AND phase='NIGHT_WEREWOLF' AND round=?
         GROUP BY target_id ORDER BY c DESC LIMIT 1"
    );
    $v->execute([$sid, $round]);
    $leadTarget = $v->fetchColumn();
    if ($leadTarget) return (int)$leadTarget;

    // Sinon : random alive non-loup
    $s = db()->prepare(
        "SELECT player_id FROM session_players
         WHERE session_id=? AND is_alive=1 AND role != 'WEREWOLF'"
    );
    $s->execute([$sid]);
    $ids = array_column($s->fetchAll(), 'player_id');
    return $ids ? (int)$ids[array_rand($ids)] : null;
}

/**
 * Pour le vote du jour :
 * - Si le bot est loup : vote pour un humain non-loup
 * - Si le bot est villageois : vote au hasard parmi les vivants (hors soi)
 */
function bot_pick_target_for_day_vote(int $sid, int $bid, string $myRole): ?int {
    if ($myRole === 'WEREWOLF') {
        $s = db()->prepare(
            "SELECT player_id FROM session_players
             WHERE session_id=? AND is_alive=1 AND role != 'WEREWOLF' AND player_id != ?"
        );
        $s->execute([$sid, $bid]);
        $ids = array_column($s->fetchAll(), 'player_id');
        return $ids ? (int)$ids[array_rand($ids)] : null;
    }
    return bot_pick_random_alive_except($sid, $bid);
}

function bot_pick_random_alive_except(int $sid, int $exceptPid): ?int {
    $s = db()->prepare(
        "SELECT player_id FROM session_players
         WHERE session_id=? AND is_alive=1 AND player_id != ?"
    );
    $s->execute([$sid, $exceptPid]);
    $ids = array_column($s->fetchAll(), 'player_id');
    return $ids ? (int)$ids[array_rand($ids)] : null;
}

function bot_has_voted(int $sid, int $pid, string $phase, int $round): bool {
    $s = db()->prepare('SELECT 1 FROM votes WHERE session_id=? AND voter_id=? AND phase=? AND round=?');
    $s->execute([$sid, $pid, $phase, $round]);
    return (bool)$s->fetchColumn();
}

function bot_has_night_action(int $sid, int $pid, string $type, int $round): bool {
    $s = db()->prepare('SELECT 1 FROM night_actions WHERE session_id=? AND player_id=? AND action_type=? AND round=?');
    $s->execute([$sid, $pid, $type, $round]);
    return (bool)$s->fetchColumn();
}

// ----- Chat des bots (contextuel, 30% de chance) -----

function bot_chat_maybe(int $sid, int $bid, string $scope, string $context): void {
    if (mt_rand(0, 99) >= 30) return;
    $msg = bot_pick_chat_line($context);
    if (!$msg) return;
    // Évite les doublons : ne pas envoyer 2 messages identiques d'affilée
    $last = db()->prepare("SELECT message FROM chat_messages WHERE session_id=? AND player_id=? ORDER BY id DESC LIMIT 1");
    $last->execute([$sid, $bid]);
    if ($last->fetchColumn() === $msg) return;

    db()->prepare('INSERT INTO chat_messages(session_id,player_id,message,scope) VALUES (?,?,?,?)')
        ->execute([$sid, $bid, $msg, $scope]);
}

function bot_pick_chat_line(string $context): ?string {
    $lines = [
        'wolf_vote' => [
            "Celui-là m'a l'air parfait.",
            "Pas de témoins cette nuit.",
            "On y va doucement.",
            "*ronronnement de loup*",
        ],
        'day_vote' => [
            "Je trouve ça suspect.",
            "Faut bien voter pour quelqu'un...",
            "Je sens le loup à plein nez.",
            "On élimine et on verra.",
            "Pas convaincu mais bon.",
            "Désolé, c'est la guerre.",
            "Mon instinct me dit oui.",
        ],
    ];
    if (!isset($lines[$context])) return null;
    $pool = $lines[$context];
    return $pool[array_rand($pool)];
}
