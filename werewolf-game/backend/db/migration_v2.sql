-- =====================================================
-- Migration v2 : ELO, parties classées, chat, historique, chasseur interactif
-- À exécuter SUR LA BASE EXISTANTE (ne supprime rien).
-- =====================================================
USE werewolf;

-- 1. ELO des joueurs (défaut 1000)
ALTER TABLE players
    ADD COLUMN elo INT NOT NULL DEFAULT 1000 AFTER avatar_url,
    ADD COLUMN games_played INT NOT NULL DEFAULT 0 AFTER elo,
    ADD COLUMN games_won INT NOT NULL DEFAULT 0 AFTER games_played;

-- 2. Parties classées + état chasseur en attente
ALTER TABLE game_sessions
    ADD COLUMN is_ranked TINYINT(1) NOT NULL DEFAULT 0 AFTER name,
    ADD COLUMN pending_hunter_id INT DEFAULT NULL AFTER winner_team,
    ADD COLUMN ended_at TIMESTAMP NULL DEFAULT NULL AFTER created_at;

-- 3. Snapshot de l'ELO sur chaque partie (pour l'historique)
ALTER TABLE session_players
    ADD COLUMN elo_before INT DEFAULT NULL,
    ADD COLUMN elo_after  INT DEFAULT NULL;

-- 4. Chat textuel
CREATE TABLE IF NOT EXISTS chat_messages (
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

-- 5. Nouvelle action de nuit pour le chasseur (déjà dans l'ENUM existante : HUNTER_SHOT)
--    Rien à ajouter ici.
