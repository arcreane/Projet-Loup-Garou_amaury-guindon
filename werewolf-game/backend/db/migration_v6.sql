-- =====================================================
-- Migration v6 : Petite Fille + Capitaine
-- À appliquer sur une BDD existante (inutile après un schema.sql frais)
-- =====================================================
USE werewolf;

-- Nouveau rôle : la Petite Fille (espionne le chat des loups la nuit)
ALTER TABLE session_players
    MODIFY COLUMN role ENUM('VILLAGER','WEREWOLF','SEER','WITCH','HUNTER','LITTLE_GIRL') DEFAULT NULL;

-- Capitaine : sa voix compte double au vote du village
ALTER TABLE session_players
    ADD COLUMN is_captain TINYINT(1) NOT NULL DEFAULT 0 AFTER is_host;

-- Fix : "passer" de la Sorcière était enregistré comme un faux WITCH_HEAL,
-- ce qui annulait l'attaque des loups à chaque nuit. Vrai type dédié :
ALTER TABLE night_actions
    MODIFY COLUMN action_type ENUM('SEER_REVEAL','WITCH_HEAL','WITCH_KILL','WITCH_PASS','HUNTER_SHOT') NOT NULL;
