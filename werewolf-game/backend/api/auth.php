<?php
// =====================================================
// Authentification : login / register
// =====================================================

function gen_token(): string {
    return bin2hex(random_bytes(24));
}

/**
 * Génère un discriminator unique pour un pseudo donné.
 * Format : 4 chiffres "0000" à "9999". Réessaie en cas de collision.
 */
function generate_discriminator(string $pseudo): string {
    for ($i = 0; $i < 30; $i++) {
        $disc = str_pad((string)random_int(0, 9999), 4, '0', STR_PAD_LEFT);
        $stmt = db()->prepare('SELECT 1 FROM players WHERE pseudo = ? AND discriminator = ?');
        $stmt->execute([$pseudo, $disc]);
        if (!$stmt->fetchColumn()) return $disc;
    }
    // Très improbable : 10000 collisions sur le même pseudo
    json_error('Trop de joueurs avec ce pseudo. Essayez un autre.', 409);
}

function handle_register(): void {
    $in = read_json();
    $pseudo = trim($in['pseudo']   ?? '');
    $email  = trim($in['email']    ?? '');
    $pass   =      $in['password'] ?? '';
    if (strlen($pseudo) < 3 || strlen($pass) < 4 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        json_error('Champs invalides', 422);
    }
    // Le pseudo ne doit pas contenir de #
    if (strpos($pseudo, '#') !== false) {
        json_error('Le pseudo ne peut pas contenir le caractère #', 422);
    }
    try {
        $hash  = password_hash($pass, PASSWORD_BCRYPT);
        $token = gen_token();
        $disc  = generate_discriminator($pseudo);
        $stmt = db()->prepare('INSERT INTO players(pseudo,discriminator,email,password_hash,token) VALUES (?,?,?,?,?)');
        $stmt->execute([$pseudo, $disc, $email, $hash, $token]);
        json_response([
            'token'         => $token,
            'player_id'     => (int)db()->lastInsertId(),
            'pseudo'        => $pseudo,
            'discriminator' => $disc,
        ]);
    } catch (PDOException $e) {
        if ($e->getCode() === '23000') {
            // Email déjà utilisé (le pseudo+disc est unique par construction)
            json_error('Email déjà utilisé', 409);
        }
        json_error('Erreur DB : ' . $e->getMessage(), 500);
    }
}

function handle_login(): void {
    $in = read_json();
    $pseudo = trim($in['pseudo']   ?? '');
    $pass   =      $in['password'] ?? '';
    if ($pseudo === '' || $pass === '') json_error('Champs requis', 422);

    // Accepte "pseudo" tout court (premier match) OU "pseudo#1234"
    $disc = null;
    if (strpos($pseudo, '#') !== false) {
        [$pseudo, $disc] = explode('#', $pseudo, 2);
        $pseudo = trim($pseudo);
        $disc   = trim($disc);
    }

    if ($disc !== null) {
        $stmt = db()->prepare('SELECT id, pseudo, discriminator, password_hash FROM players WHERE pseudo = ? AND discriminator = ?');
        $stmt->execute([$pseudo, $disc]);
    } else {
        // Plusieurs joueurs peuvent avoir le même pseudo → on prend le plus ancien
        $stmt = db()->prepare('SELECT id, pseudo, discriminator, password_hash FROM players WHERE pseudo = ? ORDER BY created_at LIMIT 1');
        $stmt->execute([$pseudo]);
    }
    $row = $stmt->fetch();
    if (!$row || !password_verify($pass, $row['password_hash'])) {
        json_error('Identifiants invalides', 401);
    }
    $token = gen_token();
    db()->prepare('UPDATE players SET token = ?, last_seen = CURRENT_TIMESTAMP WHERE id = ?')->execute([$token, $row['id']]);
    json_response([
        'token'         => $token,
        'player_id'     => (int)$row['id'],
        'pseudo'        => $row['pseudo'],
        'discriminator' => $row['discriminator'],
    ]);
}
