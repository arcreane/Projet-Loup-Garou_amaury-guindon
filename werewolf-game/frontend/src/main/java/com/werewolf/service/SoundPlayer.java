package com.werewolf.service;

import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Lecteur audio simple basé sur AudioClip (rapide, non-bloquant).
 * Les fichiers sont chargés depuis /sounds/ (place tes .wav/.mp3 dans
 * frontend/src/main/resources/sounds/). Si un fichier est absent, ça ne plante pas.
 *
 * Sons attendus (optionnels) :
 *   - night.wav    : ambiance nuit (hibou)
 *   - day.wav      : cloche du village
 *   - howl.wav     : hurlement de loup
 *   - death.wav    : mort d'un joueur
 *   - victory.wav  : fin de partie
 */
public final class SoundPlayer {

    private static final Map<String, AudioClip> CACHE = new HashMap<>();
    public static boolean enabled = true;

    private SoundPlayer() {}

    public static void play(String name) {
        if (!enabled) return;
        AudioClip clip = CACHE.computeIfAbsent(name, SoundPlayer::load);
        if (clip != null) {
            try { clip.play(0.6); } catch (Exception ignored) {}
        }
    }

    private static AudioClip load(String name) {
        try {
            URL u = SoundPlayer.class.getResource("/sounds/" + name + ".wav");
            if (u == null) u = SoundPlayer.class.getResource("/sounds/" + name + ".mp3");
            if (u == null) return null;
            return new AudioClip(u.toExternalForm());
        } catch (Exception e) {
            return null;
        }
    }
}
