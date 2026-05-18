package com.werewolf;

import com.werewolf.service.FontLoader;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage primary;

    @Override
    public void start(Stage stage) {
        FontLoader.loadAll();
        primary = stage;
        stage.setTitle("🌕 Loup-Garou");
        // Taille initiale raisonnable, mais l'utilisateur peut redimensionner
        // jusqu'à 600x400 si besoin (ou maximiser via le bouton standard)
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.setMinWidth(600);
        stage.setMinHeight(400);
        stage.setFullScreen(false);
        Router.go("login.fxml");
        stage.show();
    }

    public static Stage stage() { return primary; }

    public static void main(String[] args) { launch(args); }
}
