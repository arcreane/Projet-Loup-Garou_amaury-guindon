<?php
// =====================================================
// Router principal de l'API REST
// =====================================================
require_once __DIR__ . '/config.php';

$uri  = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$path = preg_replace('#^.*?/api/?#', '', $uri);
$path = trim($path, '/');
$verb = $_SERVER['REQUEST_METHOD'];

// --- Auth ---
if ($path === 'login'    && $verb === 'POST') { require __DIR__ . '/auth.php';     handle_login();    }
if ($path === 'register' && $verb === 'POST') { require __DIR__ . '/auth.php';     handle_register(); }

// --- Profil / Avatar ---
if ($path === 'me'              && $verb === 'GET')  { require __DIR__ . '/games.php';  handle_me();              }
if ($path === 'me/avatar'       && $verb === 'POST') { require __DIR__ . '/games.php';  handle_update_avatar();   }
if ($path === 'me/email'        && $verb === 'POST') { require __DIR__ . '/social.php'; handle_update_email();    }
if ($path === 'me/password'     && $verb === 'POST') { require __DIR__ . '/social.php'; handle_update_password(); }

// --- Amis ---
if ($path === 'friends'         && $verb === 'GET')  { require __DIR__ . '/social.php'; handle_list_friends();    }
if ($path === 'friends/request' && $verb === 'POST') { require __DIR__ . '/social.php'; handle_friend_request();  }
if ($path === 'friends/accept'  && $verb === 'POST') { require __DIR__ . '/social.php'; handle_friend_accept();   }
if ($path === 'friends/decline' && $verb === 'POST') { require __DIR__ . '/social.php'; handle_friend_decline();  }
if ($path === 'friends/remove'  && $verb === 'POST') { require __DIR__ . '/social.php'; handle_friend_remove();   }

// --- Invitations en partie ---
if ($path === 'invitations'         && $verb === 'GET')  { require __DIR__ . '/social.php'; handle_invite_list();    }
if ($path === 'invitations/send'    && $verb === 'POST') { require __DIR__ . '/social.php'; handle_invite_send();    }
if ($path === 'invitations/accept'  && $verb === 'POST') { require __DIR__ . '/social.php'; handle_invite_accept();  }
if ($path === 'invitations/decline' && $verb === 'POST') { require __DIR__ . '/social.php'; handle_invite_decline(); }

// --- Historique / Classement ---
if ($path === 'history'      && $verb === 'GET') { require __DIR__ . '/games.php'; handle_history();     }
if ($path === 'leaderboard'  && $verb === 'GET') { require __DIR__ . '/games.php'; handle_leaderboard(); }

// --- Lobby ---
if ($path === 'games' && $verb === 'GET')  { require __DIR__ . '/games.php'; handle_list_games();  }
if ($path === 'games' && $verb === 'POST') { require __DIR__ . '/games.php'; handle_create_game(); }

// --- Jeu : /games/{id}/... ---
if (preg_match('#^games/(\d+)/([a-z_]+)$#', $path, $m)) {
    $gameId = (int)$m[1];
    $action = $m[2];
    require __DIR__ . '/games.php';
    require __DIR__ . '/game_logic.php';
    switch ("$verb $action") {
        case 'POST join':    handle_join($gameId);      break;
        case 'GET players':  handle_players($gameId);   break;
        case 'GET state':    handle_state($gameId);     break;
        case 'POST start':   handle_start($gameId);     break;
        case 'POST vote':    handle_vote($gameId);      break;
        case 'GET votes':    handle_votes($gameId);     break;
        case 'POST action':  handle_action($gameId);    break;
        case 'POST chat':    handle_chat_send($gameId); break;
        default: json_error('Route inconnue', 404);
    }
}

json_error('Route inconnue', 404);
