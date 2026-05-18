package com.werewolf.service;

import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Region;
import javafx.scene.image.Image;

import java.net.URL;

/**
 * Centralise la gestion des fonds d'écran (parchemin, nuit, jour).
 * Si une image manque, on ne fait rien et le CSS s'occupe du fallback (gradient).
 */
public final class ThemeService {

    private ThemeService() {}

    public static void applyBackground(Region region, String imageName) {
        Image img = load(imageName);
        if (img == null) return;
        BackgroundSize size = new BackgroundSize(
                BackgroundSize.AUTO, BackgroundSize.AUTO,
                true, true, true, true);
        BackgroundImage bg = new BackgroundImage(
                img,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                size
        );
        region.setBackground(new Background(bg));
    }

    public static Image load(String imageName) {
        try {
            URL u = ThemeService.class.getResource("/images/" + imageName);
            return u == null ? null : new Image(u.toExternalForm());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean has(String imageName) {
        return ThemeService.class.getResource("/images/" + imageName) != null;
    }
}
