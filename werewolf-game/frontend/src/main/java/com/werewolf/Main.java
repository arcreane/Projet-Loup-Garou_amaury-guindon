package com.werewolf;

import com.werewolf.service.FontLoader;
import com.werewolf.service.ThemeService;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage primary;

    @Override
    public void start(Stage stage) {
        FontLoader.loadAll();
        ThemeService.preloadCommonBackgrounds();

        primary = stage;
        stage.setTitle("🌕 Loup-Garou");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setWidth(1280);
        stage.setHeight(800);
        stage.setFullScreen(false);
        stage.setMaximized(true);   // démarre en plein écran fenêtré (resize/min/close gardés)
        Router.go("login.fxml");
        stage.show();
    }

    public static Stage stage() { return primary; }

    public static void main(String[] args) { launch(args); }
}
