<?php
// =====================================================
// Configuration générale + helpers DB / HTTP
// =====================================================

// --- Paramètres de connexion MySQL ---
define('DB_HOST', '127.0.0.1:3307');
define('DB_NAME', 'werewolf');
define('DB_USER', 'root');
define('DB_PASS', '');

// CORS minimal pour autoriser l'appel depuis JavaFX (HttpClient)
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Content-Type: application/json; charset=utf-8');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// --- Connexion PDO ---
function db(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4';
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

// --- Helpers réponse ---
function json_response($data, int $code = 200): void {
    http_response_code($code);
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

function json_error(string $msg, int $code = 400): void {
    json_response(['error' => $msg], $code);
}

// --- Lecture du body JSON ---
function read_json(): array {
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

// --- Auth par token Bearer ---
function current_player(): ?array {
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/Bearer\s+(.+)/i', $h, $m)) return null;
    $token = trim($m[1]);
    $stmt = db()->prepare('SELECT id, pseudo, email, avatar_url FROM players WHERE token = ?');
    $stmt->execute([$token]);
    $row = $stmt->fetch();
    return $row ?: null;
}

function require_auth(): array {
    $p = current_player();
    if (!$p) json_error('Unauthorized', 401);
    return $p;
}

// --- Log d'événement ---
function game_log(int $sessionId, string $msg, string $visibility = 'ALL', ?int $target = null): void {
    db()->prepare('INSERT INTO game_logs(session_id, message, visibility, target_player_id) VALUES (?,?,?,?)')
        ->execute([$sessionId, $msg, $visibility, $target]);
}
