package com.werewolf.service;

import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Region;

import java.net.URL;

/**
 * Gestion centralisée des fonds d'écran et images thématiques.
 * Si une image est absente du classpath, on log et on ne fait rien
 * (le CSS de fallback reste appliqué).
 *
 * Structure attendue :
 *   /images/backgrounds/  - night.jpg, day.jpg, parchment.jpg, tavern.jpg
 *   /images/roles/        - villager.png, werewolf.png, seer.png, witch.png, hunter.png
 *   /images/ui/           - moon.png, sun.png, lantern.png, scroll.png, fire.png
 */
public final class ThemeService {

    private ThemeService() {}

    // ----- Helpers haut niveau -----

    public static void applyParchment(Region region) { applyBackground(region, "backgrounds/parchment.jpg", true); }
    public static void applyTavern   (Region region) { applyBackground(region, "backgrounds/tavern.jpg",    true); }
    public static void applyNight    (Region region) { applyBackground(region, "backgrounds/night.jpg",     true); }
    public static void applyDay      (Region region) { applyBackground(region, "backgrounds/day.jpg",       true); }

    /**
     * Applique une image en background avec couverture totale (style "cover").
     * @param coverFill true = remplit en gardant les proportions (peut rogner)
     */
    public static void applyBackground(Region region, String relativePath, boolean coverFill) {
        Image img = load(relativePath);
        if (img == null) return;
        BackgroundSize size = new BackgroundSize(
                BackgroundSize.AUTO, BackgroundSize.AUTO,
                true, true,
                false,        // contain = non
                coverFill     // cover = oui
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

    public static Image load(String relativePath) {
        try {
            URL u = ThemeService.class.getResource("/images/" + relativePath);
            return u == null ? null : new Image(u.toExternalForm());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean has(String relativePath) {
        return ThemeService.class.getResource("/images/" + relativePath) != null;
    }
}
