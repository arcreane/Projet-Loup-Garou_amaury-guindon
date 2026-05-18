-- =====================================================
-- Migration v5 : Timestamp pour le throttling des chats IA
-- =====================================================
USE werewolf;

ALTER TABLE game_sessions
    ADD COLUMN last_ai_chat_at TIMESTAMP NULL DEFAULT NULL;
