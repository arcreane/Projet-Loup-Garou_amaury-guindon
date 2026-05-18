package com.werewolf.service;

import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Charge les polices thématiques au démarrage.
 * Si un fichier .ttf manque, on log et on tombe sur la police système (Segoe UI).
 *
 * Polices attendues dans src/main/resources/fonts/ :
 *  - Cinzel-Bold.ttf          → titres ("font-cinzel")
 *  - IMFellEnglish-Regular.ttf → accents ("font-fell")
 *  - IMFellEnglish-Italic.ttf  → citations ("font-fell-italic")
 */
public final class FontLoader {

    public static boolean cinzelLoaded     = false;
    public static boolean fellLoaded       = false;
    public static boolean fellItalicLoaded = false;

    private FontLoader() {}

    public static void loadAll() {
        cinzelLoaded     = load("/fonts/Cinzel-Bold.ttf");
        fellLoaded       = load("/fonts/IMFellEnglish-Regular.ttf");
        fellItalicLoaded = load("/fonts/IMFellEnglish-Italic.ttf");

        System.out.println("[FontLoader] Cinzel: " + cinzelLoaded
                + " · IMFell: " + fellLoaded
                + " · IMFell-Italic: " + fellItalicLoaded);
    }

    private static boolean load(String resource) {
        try (InputStream is = FontLoader.class.getResourceAsStream(resource)) {
            if (is == null) return false;
            Font f = Font.loadFont(is, 24);
            return f != null;
        } catch (Exception e) {
            return false;
        }
    }
}
