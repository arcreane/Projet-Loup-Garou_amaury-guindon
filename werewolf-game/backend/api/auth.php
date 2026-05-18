<?php
// =====================================================
// Authentification : login / register
// =====================================================

function gen_token(): string {
    return bin2hex(random_bytes(24));
}

function handle_register(): void {
    $in = read_json();
    $pseudo = trim($in['pseudo']   ?? '');
    $email  = trim($in['email']    ?? '');
    $pass   =      $in['password'] ?? '';
    if (strlen($pseudo) < 3 || strlen($pass) < 4 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        json_error('Champs invalides', 422);
    }
    try {
        $hash  = password_hash($pass, PASSWORD_BCRYPT);
        $token = gen_token();
        $stmt = db()->prepare('INSERT INTO players(pseudo,email,password_hash,token) VALUES (?,?,?,?)');
        $stmt->execute([$pseudo, $email, $hash, $token]);
        json_response([
            'token'     => $token,
            'player_id' => (int)db()->lastInsertId(),
            'pseudo'    => $pseudo,
        ]);
    } catch (PDOException $e) {
        if ($e->getCode() === '23000') json_error('Pseudo ou email déjà utilisé', 409);
        json_error('Erreur DB : ' . $e->getMessage(), 500);
    }
}

function handle_login(): void {
    $in = read_json();
    $pseudo = trim($in['pseudo']   ?? '');
    $pass   =      $in['password'] ?? '';
    if ($pseudo === '' || $pass === '') json_error('Champs requis', 422);

    $stmt = db()->prepare('SELECT id, pseudo, password_hash FROM players WHERE pseudo = ?');
    $stmt->execute([$pseudo]);
    $row = $stmt->fetch();
    if (!$row || !password_verify($pass, $row['password_hash'])) {
        json_error('Identifiants invalides', 401);
    }
    $token = gen_token();
    db()->prepare('UPDATE players SET token = ? WHERE id = ?')->execute([$token, $row['id']]);
    json_response([
        'token'     => $token,
        'player_id' => (int)$row['id'],
        'pseudo'    => $row['pseudo'],
    ]);
}
