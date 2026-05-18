<?php
// =====================================================
// Endpoints sociaux : profil, amis, invitations
// =====================================================

// ----- Profil -----

function handle_update_email(): void {
    $me = require_auth();
    $in = read_json();
    $email = trim($in['email'] ?? '');
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) json_error('Email invalide', 422);
    try {
        db()->prepare('UPDATE players SET email = ? WHERE id = ?')->execute([$email, $me['id']]);
        json_response(['ok' => true, 'email' => $email]);
    } catch (PDOException $e) {
        if ($e->getCode() === '23000') json_error('Email déjà utilisé', 409);
        json_error('Erreur DB', 500);
    }
}

function handle_update_password(): void {
    $me = require_auth();
    $in = read_json();
    $old = $in['old_password'] ?? '';
    $new = $in['new_password'] ?? '';
    if (strlen($new) < 4) json_error('Nouveau mot de passe trop court (min 4)', 422);

    $stmt = db()->prepare('SELECT password_hash FROM players WHERE id = ?');
    $stmt->execute([$me['id']]);
    $hash = $stmt->fetchColumn();
    if (!password_verify($old, $hash)) json_error('Ancien mot de passe incorrect', 401);

    db()->prepare('UPDATE players SET password_hash = ? WHERE id = ?')
        ->execute([password_hash($new, PASSWORD_BCRYPT), $me['id']]);
    json_response(['ok' => true]);
}

// ----- Statut en ligne -----

/**
 * Renvoie 'IN_GAME', 'ONLINE' ou 'OFFLINE' selon last_seen / current_session_id.
 */
function compute_status(?string $lastSeen, $currentSessionId): string {
    if ($currentSessionId !== null) return 'IN_GAME';
    if (!$lastSeen) return 'OFFLINE';
    $age = time() - strtotime($lastSeen);
    return $age <= 30 ? 'ONLINE' : 'OFFLINE';
}

// ----- Amis -----

function handle_list_friends(): void {
    $me = require_auth();
    $mid = (int)$me['id'];

    // Amis acceptés (peut importe qui a demandé)
    $stmt = db()->prepare(
        "SELECT p.id, p.pseudo, p.discriminator, p.avatar_url, p.elo,
                p.games_played, p.games_won, p.last_seen, p.current_session_id
         FROM friendships f
         JOIN players p ON p.id = IF(f.requester_id = ?, f.addressee_id, f.requester_id)
         WHERE f.status = 'ACCEPTED' AND (f.requester_id = ? OR f.addressee_id = ?)
         ORDER BY p.pseudo"
    );
    $stmt->execute([$mid, $mid, $mid]);
    $friends = [];
    foreach ($stmt->fetchAll() as $r) {
        $friends[] = [
            'id'             => (int)$r['id'],
            'pseudo'         => $r['pseudo'],
            'discriminator'  => $r['discriminator'],
            'avatar_url'     => $r['avatar_url'],
            'elo'            => (int)$r['elo'],
            'games_played'   => (int)$r['games_played'],
            'games_won'      => (int)$r['games_won'],
            'status'         => compute_status($r['last_seen'], $r['current_session_id']),
            'session_id'     => $r['current_session_id'] ? (int)$r['current_session_id'] : null,
        ];
    }

    // Demandes reçues en attente
    $recv = db()->prepare(
        "SELECT p.id, p.pseudo, p.discriminator, p.avatar_url
         FROM friendships f JOIN players p ON p.id = f.requester_id
         WHERE f.addressee_id = ? AND f.status = 'PENDING'"
    );
    $recv->execute([$mid]);
    $received = array_map(fn($r) => [
        'id' => (int)$r['id'], 'pseudo' => $r['pseudo'],
        'discriminator' => $r['discriminator'], 'avatar_url' => $r['avatar_url']
    ], $recv->fetchAll());

    // Demandes envoyées
    $sent = db()->prepare(
        "SELECT p.id, p.pseudo, p.discriminator, p.avatar_url
         FROM friendships f JOIN players p ON p.id = f.addressee_id
         WHERE f.requester_id = ? AND f.status = 'PENDING'"
    );
    $sent->execute([$mid]);
    $sentArr = array_map(fn($r) => [
        'id' => (int)$r['id'], 'pseudo' => $r['pseudo'],
        'discriminator' => $r['discriminator'], 'avatar_url' => $r['avatar_url']
    ], $sent->fetchAll());

    json_response([
        'friends'  => $friends,
        'received' => $received,
        'sent'     => $sentArr,
    ]);
}

function handle_friend_request(): void {
    $me = require_auth();
    $in = read_json();
    $target = trim($in['pseudo'] ?? '');  // attend "pseudo#1234"
    if (!preg_match('/^(.+)#(\d{4})$/', $target, $m)) {
        json_error('Format attendu : pseudo#1234', 422);
    }
    $pseudo = trim($m[1]); $disc = $m[2];

    $stmt = db()->prepare('SELECT id FROM players WHERE pseudo = ? AND discriminator = ?');
    $stmt->execute([$pseudo, $disc]);
    $fid = $stmt->fetchColumn();
    if (!$fid) json_error('Joueur introuvable', 404);
    if ((int)$fid === (int)$me['id']) json_error('Vous ne pouvez pas vous ajouter vous-même', 422);

    // Si une demande existe déjà dans l'autre sens : on l'accepte directement
    $rev = db()->prepare('SELECT status FROM friendships WHERE requester_id = ? AND addressee_id = ?');
    $rev->execute([(int)$fid, (int)$me['id']]);
    $revStatus = $rev->fetchColumn();
    if ($revStatus === 'PENDING') {
        db()->prepare("UPDATE friendships SET status='ACCEPTED' WHERE requester_id=? AND addressee_id=?")
            ->execute([(int)$fid, (int)$me['id']]);
        json_response(['ok' => true, 'auto_accepted' => true]);
    }
    if ($revStatus === 'ACCEPTED') {
        json_response(['ok' => true, 'already_friends' => true]);
    }

    try {
        db()->prepare('INSERT INTO friendships(requester_id, addressee_id, status) VALUES (?,?,"PENDING")')
            ->execute([(int)$me['id'], (int)$fid]);
        json_response(['ok' => true]);
    } catch (PDOException $e) {
        if ($e->getCode() === '23000') json_error('Demande déjà envoyée', 409);
        json_error('Erreur DB', 500);
    }
}

function handle_friend_accept(): void {
    $me = require_auth();
    $in = read_json();
    $fid = (int)($in['player_id'] ?? 0);
    if (!$fid) json_error('player_id requis', 422);
    $r = db()->prepare("UPDATE friendships SET status='ACCEPTED' WHERE requester_id=? AND addressee_id=? AND status='PENDING'");
    $r->execute([$fid, (int)$me['id']]);
    if ($r->rowCount() === 0) json_error('Demande introuvable', 404);
    json_response(['ok' => true]);
}

function handle_friend_decline(): void {
    $me = require_auth();
    $in = read_json();
    $fid = (int)($in['player_id'] ?? 0);
    if (!$fid) json_error('player_id requis', 422);
    db()->prepare('DELETE FROM friendships WHERE requester_id=? AND addressee_id=? AND status="PENDING"')
        ->execute([$fid, (int)$me['id']]);
    json_response(['ok' => true]);
}

function handle_friend_remove(): void {
    $me = require_auth();
    $in = read_json();
    $fid = (int)($in['player_id'] ?? 0);
    if (!$fid) json_error('player_id requis', 422);
    db()->prepare(
        "DELETE FROM friendships
         WHERE (requester_id=? AND addressee_id=?) OR (requester_id=? AND addressee_id=?)"
    )->execute([(int)$me['id'], $fid, $fid, (int)$me['id']]);
    json_response(['ok' => true]);
}

// ----- Invitations en partie -----

function handle_invite_send(): void {
    $me = require_auth();
    $in = read_json();
    $fid = (int)($in['player_id'] ?? 0);
    $sid = (int)($in['session_id'] ?? 0);
    if (!$fid || !$sid) json_error('player_id et session_id requis', 422);

    // Vérifie qu'on est ami avec lui
    $ok = db()->prepare(
        "SELECT 1 FROM friendships WHERE status='ACCEPTED'
         AND ((requester_id=? AND addressee_id=?) OR (requester_id=? AND addressee_id=?))"
    );
    $ok->execute([(int)$me['id'], $fid, $fid, (int)$me['id']]);
    if (!$ok->fetchColumn()) json_error('Ce joueur n\'est pas votre ami', 403);

    // La partie doit être en WAITING
    $g = db()->prepare('SELECT status FROM game_sessions WHERE id=?');
    $g->execute([$sid]);
    if ($g->fetchColumn() !== 'WAITING') json_error('Partie déjà lancée ou close', 409);

    db()->prepare('INSERT INTO game_invitations(from_id, to_id, session_id) VALUES (?,?,?)')
        ->execute([(int)$me['id'], $fid, $sid]);
    json_response(['ok' => true]);
}

function handle_invite_list(): void {
    $me = require_auth();
    $stmt = db()->prepare(
        "SELECT i.id, i.session_id, g.name AS session_name, g.is_ranked,
                p.id AS from_id, p.pseudo AS from_pseudo, p.discriminator AS from_disc, p.avatar_url
         FROM game_invitations i
         JOIN players p ON p.id = i.from_id
         JOIN game_sessions g ON g.id = i.session_id
         WHERE i.to_id = ? AND i.status = 'PENDING' AND g.status = 'WAITING'
         ORDER BY i.created_at DESC"
    );
    $stmt->execute([(int)$me['id']]);
    json_response(['invitations' => $stmt->fetchAll()]);
}

function handle_invite_accept(): void {
    $me = require_auth();
    $in = read_json();
    $iid = (int)($in['invitation_id'] ?? 0);
    if (!$iid) json_error('invitation_id requis', 422);

    $stmt = db()->prepare("SELECT session_id FROM game_invitations WHERE id=? AND to_id=? AND status='PENDING'");
    $stmt->execute([$iid, (int)$me['id']]);
    $sid = $stmt->fetchColumn();
    if (!$sid) json_error('Invitation introuvable', 404);

    db()->prepare("UPDATE game_invitations SET status='ACCEPTED' WHERE id=?")->execute([$iid]);
    // Tente de rejoindre
    try {
        db()->prepare('INSERT INTO session_players(session_id, player_id) VALUES (?,?)')
            ->execute([(int)$sid, (int)$me['id']]);
    } catch (PDOException $e) { /* déjà dedans */ }
    json_response(['ok' => true, 'session_id' => (int)$sid]);
}

function handle_invite_decline(): void {
    $me = require_auth();
    $in = read_json();
    $iid = (int)($in['invitation_id'] ?? 0);
    if (!$iid) json_error('invitation_id requis', 422);
    db()->prepare("UPDATE game_invitations SET status='DECLINED' WHERE id=? AND to_id=?")
        ->execute([$iid, (int)$me['id']]);
    json_response(['ok' => true]);
}
