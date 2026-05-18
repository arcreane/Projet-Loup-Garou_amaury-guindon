package com.werewolf;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
                scene.getStylesheets().add(
                    Objects.requireNonNull(Router.class.getResource("/css/dark-theme.css")).toExternalForm()
                );
                stage.setScene(scene);
            } else {
                // Réutilise la scène pour conserver dimensions, position et plein-écran
                scene.setRoot(root);
            }
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
}
