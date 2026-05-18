-- =====================================================
-- Werewolf Multiplayer Game - Database Schema (v2)
-- MySQL 5.7+ / 8.0+ - inclut ELO, ranked, chat, historique
-- =====================================================

DROP DATABASE IF EXISTS werewolf;
CREATE DATABASE werewolf CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE werewolf;

-- =====================================================
-- PLAYERS
-- =====================================================
CREATE TABLE players (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    pseudo          VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(255) DEFAULT NULL,
    elo             INT NOT NULL DEFAULT 1000,
    games_played    INT NOT NULL DEFAULT 0,
    games_won       INT NOT NULL DEFAULT 0,
    token           VARCHAR(64)  DEFAULT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token),
    INDEX idx_elo   (elo)
) ENGINE=InnoDB;

-- =====================================================
-- GAME SESSIONS
-- =====================================================
CREATE TABLE game_sessions (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(80) NOT NULL DEFAULT 'Partie sans nom',
    is_ranked        TINYINT(1)  NOT NULL DEFAULT 0,
    status           ENUM('WAITING','NIGHT','DAY','ENDED') NOT NULL DEFAULT 'WAITING',
    phase            VARCHAR(40)  DEFAULT NULL,
    round            INT NOT NULL DEFAULT 0,
    phase_started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    phase_duration   INT NOT NULL DEFAULT 60,
    winner_team      ENUM('VILLAGERS','WEREWOLVES') DEFAULT NULL,
    pending_hunter_id INT DEFAULT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at         TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- =====================================================
-- SESSION PLAYERS
-- =====================================================
CREATE TABLE session_players (
    session_id      INT NOT NULL,
    player_id       INT NOT NULL,
    role            ENUM('VILLAGER','WEREWOLF','SEER','WITCH','HUNTER') DEFAULT NULL,
    is_alive        TINYINT(1) NOT NULL DEFAULT 1,
    is_host         TINYINT(1) NOT NULL DEFAULT 0,
    joined_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    witch_heal_used TINYINT(1) NOT NULL DEFAULT 0,
    witch_kill_used TINYINT(1) NOT NULL DEFAULT 0,
    elo_before      INT DEFAULT NULL,
    elo_after       INT DEFAULT NULL,
    PRIMARY KEY (session_id, player_id),
    FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id)  REFERENCES players(id)       ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================
-- VOTES
-- =====================================================
CREATE TABLE votes (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    session_id      INT NOT NULL,
    voter_id        INT NOT NULL,
    target_id       INT NOT NULL,
    phase           VARCHAR(40) NOT NULL,
    round           INT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_vote (session_id, voter_id, phase, round),
    FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (voter_id)   REFERENCES players(id)       ON DELETE CASCADE,
    FOREIGN KEY (target_id)  REFERENCES players(id)       ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================
-- ACTIONS DE NUIT
-- =====================================================
CREATE TABLE night_actions (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    session_id      INT NOT NULL,
    player_id       INT NOT NULL,
    action_type     ENUM('SEER_REVEAL','WITCH_HEAL','WITCH_KILL','HUNTER_SHOT') NOT NULL,
    target_id       INT DEFAULT NULL,
    round           INT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id)  REFERENCES players(id)       ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================
-- GAME LOGS
-- =====================================================
CREATE TABLE game_logs (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    session_id       INT NOT NULL,
    message          VARCHAR(255) NOT NULL,
    visibility       ENUM('ALL','WEREWOLVES','SELF') NOT NULL DEFAULT 'ALL',
    target_player_id INT DEFAULT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB;

-- =====================================================
-- CHAT TEXTUEL
-- =====================================================
CREATE TABLE chat_messages (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    session_id INT NOT NULL,
    player_id  INT NOT NULL,
    message    VARCHAR(255) NOT NULL,
    scope      ENUM('ALL','WEREWOLVES') NOT NULL DEFAULT 'ALL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id)  REFERENCES players(id)       ON DELETE CASCADE,
    INDEX idx_session_created (session_id, created_at)
) ENGINE=InnoDB;
