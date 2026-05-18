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
        Router.go("login.fxml");
        stage.setMinWidth(1024);
        stage.setMinHeight(720);
        stage.show();
    }

    public static Stage stage() { return primary; }

    public static void main(String[] args) { launch(args); }
}
