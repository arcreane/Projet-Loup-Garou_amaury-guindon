-- =====================================================
-- Migration v3 : discriminator, statut en ligne, amitiés, invitations
-- À exécuter SUR LA BASE EXISTANTE.
-- =====================================================
USE werewolf;

-- 1. Discriminator type Riot (#0042) + statut en ligne
ALTER TABLE players
    ADD COLUMN discriminator CHAR(4) DEFAULT NULL AFTER pseudo,
    ADD COLUMN last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP AFTER created_at,
    ADD COLUMN current_session_id INT DEFAULT NULL;

-- 2. Génère un discriminator pour les joueurs existants
UPDATE players
SET discriminator = LPAD(FLOOR(RAND() * 10000), 4, '0')
WHERE discriminator IS NULL;

-- 3. Remplace l'unicité (pseudo) par (pseudo, discriminator)
ALTER TABLE players
    DROP INDEX pseudo,
    ADD UNIQUE KEY uq_pseudo_disc (pseudo, discriminator);

ALTER TABLE players MODIFY COLUMN discriminator CHAR(4) NOT NULL;

-- 4. Table d'amitiés (relation symétrique : on ne stocke qu'une seule ligne)
CREATE TABLE friendships (
    requester_id INT NOT NULL,
    addressee_id INT NOT NULL,
    status       ENUM('PENDING','ACCEPTED') NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (requester_id, addressee_id),
    FOREIGN KEY (requester_id) REFERENCES players(id) ON DELETE CASCADE,
    FOREIGN KEY (addressee_id) REFERENCES players(id) ON DELETE CASCADE,
    INDEX idx_addressee_status (addressee_id, status)
) ENGINE=InnoDB;

-- 5. Invitations en partie
CREATE TABLE game_invitations (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    from_id     INT NOT NULL,
    to_id       INT NOT NULL,
    session_id  INT NOT NULL,
    status      ENUM('PENDING','ACCEPTED','DECLINED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_id)    REFERENCES players(id)       ON DELETE CASCADE,
    FOREIGN KEY (to_id)      REFERENCES players(id)       ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE CASCADE,
    INDEX idx_to_status (to_id, status)
) ENGINE=InnoDB;
