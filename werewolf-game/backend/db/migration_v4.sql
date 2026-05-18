-- =====================================================
-- Migration v4 : Bots IA pour mode entraînement
-- À exécuter sur la base existante.
-- =====================================================
USE werewolf;

ALTER TABLE players ADD COLUMN is_bot TINYINT(1) NOT NULL DEFAULT 0;

-- Création de 10 comptes bots. Le password_hash n'est PAS un vrai bcrypt
-- (volontaire : personne ne peut s'identifier comme un bot).
-- Le discriminator 9001-9010 sert d'identifiant unique.
INSERT INTO players (pseudo, discriminator, email, password_hash, avatar_url, is_bot, elo) VALUES
('Aurore',   '9001', 'bot1@werewolf.ai',  '!BOT_NO_LOGIN!', '🌸', 1, 1000),
('Brutus',   '9002', 'bot2@werewolf.ai',  '!BOT_NO_LOGIN!', '🐺', 1, 1000),
('Celeste',  '9003', 'bot3@werewolf.ai',  '!BOT_NO_LOGIN!', '✨', 1, 1000),
('Dimitri',  '9004', 'bot4@werewolf.ai',  '!BOT_NO_LOGIN!', '🗡', 1, 1000),
('Elara',    '9005', 'bot5@werewolf.ai',  '!BOT_NO_LOGIN!', '🦉', 1, 1000),
('Faust',    '9006', 'bot6@werewolf.ai',  '!BOT_NO_LOGIN!', '🧙', 1, 1000),
('Gaia',     '9007', 'bot7@werewolf.ai',  '!BOT_NO_LOGIN!', '🌿', 1, 1000),
('Helios',   '9008', 'bot8@werewolf.ai',  '!BOT_NO_LOGIN!', '☀',  1, 1000),
('Iris',     '9009', 'bot9@werewolf.ai',  '!BOT_NO_LOGIN!', '🌈', 1, 1000),
('Jorus',    '9010', 'bota@werewolf.ai',  '!BOT_NO_LOGIN!', '🦊', 1, 1000);
