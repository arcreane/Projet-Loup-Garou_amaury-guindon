<?php
// =====================================================
// IA conversationnelle pour les bots via Google Gemini
//
// Si GEMINI_API_KEY est défini, les bots génèrent leurs
// messages via l'API Gemini en réagissant au contexte de
// la partie. Sinon, fallback sur les phrases pré-écrites.
// =====================================================

/**
 * Déclenche un message IA pour un bot vivant aléatoire en réaction
 * à un événement de jeu. NoOp si Gemini désactivé.
 *
 * @param string $eventType  death | phase_change | vote_cast | game_start
 * @param array  $payload    données contextuelles de l'événement
 */
function ai_event(int $sid, string $eventType, array $payload = []): void {
    if (!ai_enabled()) return;

    // Évite de spammer : max 1 message IA toutes les 3 secondes par partie
    $g = db()->prepare('SELECT last_ai_chat_at FROM game_sessions WHERE id=?');
    $g->execute([$sid]);
    $row = $g->fetch();
    if ($row && $row['last_ai_chat_at'] && (time() - strtotime($row['last_ai_chat_at']) < 3)) {
        return;
    }

    // Pick 1 ou 2 bots vivants au hasard pour réagir
    $count = ($eventType === 'death' || $eventType === 'phase_change') ? mt_rand(1, 2) : 1;
    $bots = db()->prepare(
        "SELECT p.id, p.pseudo, sp.role
         FROM session_players sp JOIN players p ON p.id = sp.player_id
         WHERE sp.session_id = ? AND p.is_bot = 1 AND sp.is_alive = 1
         ORDER BY RAND() LIMIT " . (int)$count
    );
    $bots->execute([$sid]);
    $rows = $bots->fetchAll();
    if (!$rows) return;

    foreach ($rows as $bot) {
        ai_generate_chat($sid, (int)$bot['id'], $bot['pseudo'], $bot['role'], $eventType, $payload);
    }

    db()->prepare('UPDATE game_sessions SET last_ai_chat_at = CURRENT_TIMESTAMP WHERE id=?')->execute([$sid]);
}

function ai_enabled(): bool {
    return defined('GEMINI_API_KEY') && GEMINI_API_KEY !== '' && stripos(GEMINI_API_KEY, 'COLLE') === false;
}

/**
 * Appelle Gemini avec le contexte de la partie pour générer un message
 * et l'insère dans le chat. Le scope est WEREWOLVES pour les loups la
 * nuit, ALL sinon.
 */
function ai_generate_chat(int $sid, int $botId, string $pseudo, ?string $role, string $eventType, array $payload): void {
    $prompt = ai_build_prompt($sid, $pseudo, $role, $eventType, $payload);
    $msg = ai_call_gemini($prompt);
    if (!$msg) return;

    // Nettoie : retire les retours à la ligne, limite à 240 chars
    $msg = trim(preg_replace('/\s+/', ' ', $msg));
    if (mb_strlen($msg) > 240) $msg = mb_substr($msg, 0, 237) . '...';
    if ($msg === '') return;

    // Scope : loups la nuit parlent en privé, sinon ALL
    $session = db()->prepare('SELECT status, phase FROM game_sessions WHERE id=?');
    $session->execute([$sid]);
    $s = $session->fetch();
    $isNight = isset($s['status']) && $s['status'] === 'NIGHT';
    $scope = ($isNight && $role === 'WEREWOLF') ? 'WEREWOLVES' : 'ALL';

    db()->prepare('INSERT INTO chat_messages(session_id,player_id,message,scope) VALUES (?,?,?,?)')
        ->execute([$sid, $botId, $msg, $scope]);
}

function ai_build_prompt(int $sid, string $pseudo, ?string $role, string $eventType, array $payload): string {
    // Joueurs vivants
    $alive = db()->prepare(
        "SELECT p.pseudo FROM session_players sp JOIN players p ON p.id = sp.player_id
         WHERE sp.session_id = ? AND sp.is_alive = 1 ORDER BY sp.joined_at"
    );
    $alive->execute([$sid]);
    $aliveNames = implode(', ', array_column($alive->fetchAll(), 'pseudo'));

    // 8 derniers logs publics
    $logs = db()->prepare(
        "SELECT message FROM game_logs WHERE session_id = ? AND visibility = 'ALL'
         ORDER BY id DESC LIMIT 8"
    );
    $logs->execute([$sid]);
    $recentLogs = array_reverse(array_column($logs->fetchAll(), 'message'));

    // 6 derniers messages publics du chat
    $chat = db()->prepare(
        "SELECT p.pseudo, c.message FROM chat_messages c JOIN players p ON p.id = c.player_id
         WHERE c.session_id = ? AND c.scope = 'ALL'
         ORDER BY c.id DESC LIMIT 6"
    );
    $chat->execute([$sid]);
    $chatLines = [];
    foreach (array_reverse($chat->fetchAll()) as $r) {
        $chatLines[] = $r['pseudo'] . ': ' . $r['message'];
    }

    $session = db()->prepare('SELECT status, phase, round FROM game_sessions WHERE id=?');
    $session->execute([$sid]);
    $s = $session->fetch();

    $eventDesc = ai_event_description($eventType, $payload);

    $roleLabel = ai_role_label($role);

    return "Tu joues à une partie de Loup-Garou (en français). Tu es \"{$pseudo}\".\n"
        . "TON RÔLE SECRET : {$roleLabel}.\n"
        . "Tour : {$s['round']}, phase : {$s['phase']}, statut : {$s['status']}.\n"
        . "Joueurs encore en vie : {$aliveNames}.\n"
        . "\n"
        . "Évènements récents :\n- " . implode("\n- ", $recentLogs) . "\n"
        . "\n"
        . "Chat récent :\n" . (empty($chatLines) ? "(vide)" : implode("\n", $chatLines)) . "\n"
        . "\n"
        . "ÉVÈNEMENT QUI VIENT DE SE PASSER : {$eventDesc}\n"
        . "\n"
        . "Réagis en UNE phrase courte (max 18 mots), en français, comme un VRAI joueur. "
        . "Reste dans ton personnage : si tu es loup-garou, ne révèle JAMAIS ton rôle, bluff, manipule. "
        . "Si tu es villageois, exprime tes doutes ou affirme ton innocence. "
        . "Pas de guillemets, pas d'emojis, juste ta phrase. "
        . "Sois naturel et bref.\nTa phrase :";
}

function ai_role_label(?string $r): string {
    switch ($r) {
        case 'WEREWOLF': return 'Loup-Garou (tu veux dévorer le village SANS te faire démasquer)';
        case 'SEER':     return 'Voyante (tu connais le rôle d\'un joueur, à utiliser stratégiquement)';
        case 'WITCH':    return 'Sorcière (potions de vie et de mort, à utiliser sagement)';
        case 'HUNTER':   return 'Chasseur (tu tireras une dernière flèche à ta mort)';
        case 'VILLAGER': return 'simple Villageois (tu dois trouver les loups par déduction)';
        default:         return 'inconnu';
    }
}

function ai_event_description(string $eventType, array $payload): string {
    switch ($eventType) {
        case 'game_start':
            return "La partie commence. Première nuit qui tombe.";
        case 'phase_change':
            $phase = $payload['phase'] ?? '?';
            $labels = [
                'NIGHT_WEREWOLF' => 'La nuit tombe, les loups vont chasser.',
                'NIGHT_SEER'     => 'La Voyante se réveille pour sonder un joueur.',
                'NIGHT_WITCH'    => 'La Sorcière se réveille avec ses potions.',
                'DAY_VOTE'       => 'Le jour se lève, le village va voter pour éliminer un suspect.',
            ];
            return $labels[$phase] ?? "Changement de phase : {$phase}";
        case 'death':
            $name = $payload['pseudo'] ?? 'Un joueur';
            $role = ai_role_label($payload['role'] ?? null);
            $cause = $payload['cause'] ?? 'est mort';
            return "{$name} est mort ({$cause}). Il était : {$role}.";
        case 'vote_cast':
            $voter = $payload['voter'] ?? '?';
            $target = $payload['target'] ?? '?';
            return "{$voter} vient de voter contre {$target}.";
    }
    return "Évènement inconnu.";
}

/**
 * Appelle l'API Gemini en POST cURL. Retourne le texte généré ou null en cas d'erreur.
 * Timeout court (8s) pour ne pas bloquer le jeu.
 */
function ai_call_gemini(string $prompt): ?string {
    $url = 'https://generativelanguage.googleapis.com/v1beta/models/'
         . rawurlencode(GEMINI_MODEL) . ':generateContent?key=' . rawurlencode(GEMINI_API_KEY);

    $body = json_encode([
        'contents' => [['parts' => [['text' => $prompt]]]],
        'generationConfig' => [
            'temperature'     => 0.95,
            'maxOutputTokens' => 80,
            'topP'            => 0.95,
        ],
        'safetySettings' => [
            ['category' => 'HARM_CATEGORY_HARASSMENT',        'threshold' => 'BLOCK_ONLY_HIGH'],
            ['category' => 'HARM_CATEGORY_HATE_SPEECH',       'threshold' => 'BLOCK_ONLY_HIGH'],
            ['category' => 'HARM_CATEGORY_SEXUALLY_EXPLICIT', 'threshold' => 'BLOCK_ONLY_HIGH'],
            ['category' => 'HARM_CATEGORY_DANGEROUS_CONTENT', 'threshold' => 'BLOCK_ONLY_HIGH'],
        ],
    ], JSON_UNESCAPED_UNICODE);

    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST           => true,
        CURLOPT_POSTFIELDS     => $body,
        CURLOPT_HTTPHEADER     => ['Content-Type: application/json'],
        CURLOPT_TIMEOUT        => 8,
        CURLOPT_CONNECTTIMEOUT => 5,
    ]);
    $resp = curl_exec($ch);
    if ($resp === false) { curl_close($ch); return null; }
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($code !== 200) return null;

    $data = json_decode($resp, true);
    return $data['candidates'][0]['content']['parts'][0]['text'] ?? null;
}
