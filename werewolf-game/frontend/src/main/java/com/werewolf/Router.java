package com.werewolf;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;

/**
 * Mini routeur : remplace le contenu de la scène courante par la FXML demandée.
 * Préserve la taille de la fenêtre et l'état plein-écran entre transitions
 * (en réutilisant la même Scene au lieu d'en créer une nouvelle).
 */
public final class Router {

    private Router() {}

    public static <T> T go(String fxml) {
        return go(fxml, null);
    }

    public static <T> T go(String fxml, Object payload) {
        try {
            URL res = Router.class.getResource("/fxml/" + fxml);
            Objects.requireNonNull(res, "FXML introuvable : " + fxml);
            FXMLLoader loader = new FXMLLoader(res);
            Parent root = loader.load();

            Object ctrl = loader.getController();
            if (ctrl instanceof PayloadAware<?> aware && payload != null) {
                @SuppressWarnings("unchecked")
                PayloadAware<Object> p = (PayloadAware<Object>) aware;
                p.init(payload);
            }

            Stage stage = Main.stage();
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1280, 800);
                scene.setFill(Color.web("#0a0610"));   // coins arrondis du Win 11 → ce noir profond les masque
                scene.getStylesheets().add(
                    Objects.requireNonNull(Router.class.getResource("/css/dark-theme.css")).toExternalForm()
                );
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            applyRoundedClip(root, stage);
            @SuppressWarnings("unchecked")
            T c = (T) ctrl;
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Erreur de navigation vers " + fxml, e);
        }
    }

    public interface PayloadAware<P> {
        void init(P payload);
    }

    /**
     * Applique un clip arrondi sur le contenu pour épouser les coins arrondis
     * des fenêtres Windows 11. Désactive le clip quand la fenêtre est maximisée
     * (les coins redeviennent droits côté OS).
     */
    private static void applyRoundedClip(Parent root, Stage stage) {
        if (!(root instanceof Region region)) return;

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());

        Runnable update = () -> {
            if (stage.isMaximized() || stage.isFullScreen()) {
                clip.setArcWidth(0);
                clip.setArcHeight(0);
            } else {
                clip.setArcWidth(20);
                clip.setArcHeight(20);
            }
        };
        update.run();
        stage.maximizedProperty().addListener((o, a, b) -> update.run());
        stage.fullScreenProperty().addListener((o, a, b) -> update.run());

        region.setClip(clip);
    }
}
