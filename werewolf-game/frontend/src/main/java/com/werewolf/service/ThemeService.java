package com.werewolf.service;

import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestion centralisée des fonds d'écran et images thématiques.
 * Cache permanent + chargement synchrone pour avoir les dimensions
 * dès la première frame (sinon JavaFX scale mal en mode "cover").
 */
public final class ThemeService {

    private static final Map<String, Image> CACHE = new HashMap<>();

    /** Couleur de remplissage si l'image ne couvre pas (debug visuel : ne devrait jamais apparaître). */
    private static final Color FALLBACK_FILL = Color.web("#0a0610");

    private ThemeService() {}

    public static void applyParchment(Region region) { applyClass(region, "bg-parchment"); }
    public static void applyTavern   (Region region) { applyClass(region, "bg-tavern");    }
    public static void applyNight    (Region region) { applyClass(region, "bg-night");     }
    public static void applyDay      (Region region) { applyClass(region, "bg-day");       }
    public static void applyCampfire (Region region) { applyClass(region, "bg-campfire");  }

    /** Toggle exclusif d'une classe bg-* sur la région (couverture native via CSS). */
    public static void applyClass(Region region, String cssClass) {
        region.getStyleClass().removeAll("bg-night","bg-day","bg-parchment","bg-tavern","bg-campfire");
        region.getStyleClass().add(cssClass);
    }

    /**
     * Applique une image en background avec couverture totale (style "cover" CSS).
     * Une couche de couleur unie est posée derrière l'image au cas où la couverture
     * échoue (race condition rare).
     */
    public static void applyBackground(Region region, String relativePath, boolean coverFill) {
        Image img = load(relativePath);
        BackgroundFill fill = new BackgroundFill(FALLBACK_FILL, CornerRadii.EMPTY, null);
        if (img == null) {
            region.setBackground(new Background(fill));
            return;
        }
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
        // Couleur unie en couche inférieure + image par-dessus
        region.setBackground(new Background(
                new BackgroundFill[]{ fill },
                new BackgroundImage[]{ bg }
        ));
    }

    public static Image roleImage(String roleName) {
        if (roleName == null) return null;
        return load("roles/" + roleName.toLowerCase() + ".png");
    }

    /** Charge SYNCHRONE avec cache. Bloque le thread UI le temps du décodage, ce qui est
     *  acceptable pour les fonds d'écran chargés une seule fois au début d'un écran. */
    public static Image load(String relativePath) {
        Image cached = CACHE.get(relativePath);
        if (cached != null) return cached;
        try {
            URL u = ThemeService.class.getResource("/images/" + relativePath);
            if (u == null) return null;
            Image img = new Image(u.toExternalForm());  // synchrone
            CACHE.put(relativePath, img);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    /** Pré-charge tous les fonds principaux en parallèle au démarrage. */
    public static void preloadCommonBackgrounds() {
        new Thread(() -> {
            load("backgrounds/night.jpg");
            load("backgrounds/day.jpg");
            load("backgrounds/parchment.jpg");
            load("backgrounds/tavern.jpg");
            load("ui/moon.png");
            load("ui/sun.png");
        }, "image-preload").start();
    }

    public static boolean has(String relativePath) {
        return ThemeService.class.getResource("/images/" + relativePath) != null;
    }
}
