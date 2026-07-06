<?php
// =====================================================
// Configuration générale + helpers DB / HTTP
// =====================================================

// --- Charger les secrets locaux EN PREMIER (gitignored) ---
// Pour activer l'IA Gemini : copie config.local.example.php en
// config.local.php et renseigne ta clé GEMINI_API_KEY.
$__localConfig = __DIR__ . '/config.local.php';
if (file_exists($__localConfig)) require_once $__localConfig;

// --- Paramètres de connexion MySQL ---
if (!defined('DB_HOST')) define('DB_HOST', '127.0.0.1:3307');
if (!defined('DB_NAME')) define('DB_NAME', 'werewolf');
if (!defined('DB_USER')) define('DB_USER', 'root');
if (!defined('DB_PASS')) define('DB_PASS', '');

// --- Clé API Gemini (chat IA des bots). Vide = bots utilisent les phrases pré-écrites ---
if (!defined('GEMINI_API_KEY')) define('GEMINI_API_KEY', '');
// gemini-2.5-flash-lite : au tier gratuit, sans "thinking" (réponse directe, latence minimale)
if (!defined('GEMINI_MODEL'))   define('GEMINI_MODEL', 'gemini-2.5-flash-lite');

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
    $stmt = db()->prepare('SELECT id, pseudo, discriminator, email, avatar_url FROM players WHERE token = ?');
    $stmt->execute([$token]);
    $row = $stmt->fetch();
    if (!$row) return null;
    // Rafraîchit le heartbeat pour le statut "en ligne"
    db()->prepare('UPDATE players SET last_seen = CURRENT_TIMESTAMP WHERE id = ?')->execute([$row['id']]);
    return $row;
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
