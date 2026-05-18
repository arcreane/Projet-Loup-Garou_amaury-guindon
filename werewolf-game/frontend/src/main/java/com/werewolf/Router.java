package com.werewolf;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.net.URL;
import java.util.Objects;

/**
 * Mini routeur : remplace la scène courante par la FXML fournie.
 * Applique automatiquement le CSS du thème sombre.
 */
public final class Router {

    private Router() {}

    public static <T> T go(String fxml) {
        return go(fxml, null);
    }

    /** Charge une FXML et passe optionnellement un payload au controller via Initializable. */
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

            Scene scene = new Scene(root, 1024, 720);
            scene.getStylesheets().add(
                Objects.requireNonNull(Router.class.getResource("/css/dark-theme.css")).toExternalForm()
            );
            Main.stage().setScene(scene);
            @SuppressWarnings("unchecked")
            T c = (T) ctrl;
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Erreur de navigation vers " + fxml, e);
        }
    }

    /** Interface optionnelle pour qu'un controller reçoive un payload au chargement. */
    public interface PayloadAware<P> {
        void init(P payload);
    }
}
