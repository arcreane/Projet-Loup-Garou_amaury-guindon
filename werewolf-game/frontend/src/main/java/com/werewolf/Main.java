package com.werewolf;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Point d'entrée JavaFX. Charge l'écran de connexion.
 * Le {@link Router} gère ensuite la navigation entre écrans.
 */
public class Main extends Application {

    private static Stage primary;

    @Override
    public void start(Stage stage) throws Exception {
        primary = stage;
        stage.setTitle("Loup-Garou Online");
        Router.go("login.fxml");
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        stage.show();
    }

    public static Stage stage() { return primary; }

    public static void main(String[] args) { launch(args); }
}
