package com.werewolf.service;

import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Region;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestion centralisée des fonds d'écran et images thématiques.
 * Toutes les images sont cachées : on ne décode jamais 2 fois le même PNG/JPG.
 *
 * Structure attendue :
 *   /images/backgrounds/  - night.jpg, day.jpg, parchment.jpg, tavern.jpg
 *   /images/roles/        - villager.png, werewolf.png, seer.png, witch.png, hunter.png
 *   /images/ui/           - moon.png, sun.png, lantern.png, scroll.png, fire.png, campfire_*.gif
 */
public final class ThemeService {

    private static final Map<String, Image> CACHE = new HashMap<>();

    private ThemeService() {}

    public static void applyParchment(Region region) { applyBackground(region, "backgrounds/parchment.jpg", true); }
    public static void applyTavern   (Region region) { applyBackground(region, "backgrounds/tavern.jpg",    true); }
    public static void applyNight    (Region region) { applyBackground(region, "backgrounds/night.jpg",     true); }
    public static void applyDay      (Region region) { applyBackground(region, "backgrounds/day.jpg",       true); }

    public static void applyBackground(Region region, String relativePath, boolean coverFill) {
        Image img = load(relativePath);
        if (img == null) return;
        BackgroundSize size = new BackgroundSize(
                BackgroundSize.AUTO, BackgroundSize.AUTO,
                true, true,
                false,
                coverFill
        );
        BackgroundImage bg = new BackgroundImage(
                img,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                size
        );
        region.setBackground(new Background(bg));
    }

    public static Image roleImage(String roleName) {
        if (roleName == null) return null;
        return load("roles/" + roleName.toLowerCase() + ".png");
    }

    /** Charge une image avec cache. Aucun décodage répété. */
    public static Image load(String relativePath) {
        Image cached = CACHE.get(relativePath);
        if (cached != null) return cached;
        try {
            URL u = ThemeService.class.getResource("/images/" + relativePath);
            if (u == null) return null;
            // background loading + cache permanent
            Image img = new Image(u.toExternalForm(), true);
            CACHE.put(relativePath, img);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean has(String relativePath) {
        return ThemeService.class.getResource("/images/" + relativePath) != null;
    }
}
