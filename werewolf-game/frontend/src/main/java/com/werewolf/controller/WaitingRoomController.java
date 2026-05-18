package com.werewolf.controller;

import com.werewolf.Router;
import com.werewolf.model.Player;
import com.werewolf.model.StateResponse;
import com.werewolf.service.GameService;
import com.werewolf.service.Session;
import com.werewolf.service.ThemeService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

/**
 * Salle d'attente : liste les joueurs et lance la partie quand l'hôte clique sur START.
 * Polling /state toutes les 2 s, redirection vers GameScreen dès que status != WAITING.
 */
public class WaitingRoomController {

    @FXML private Label gameLabel;
    @FXML private Label countLabel;
    @FXML private ListView<Player> playerList;
    @FXML private Button startBtn;
    @FXML private Button leaveBtn;
    @FXML private Label errorLabel;
    @FXML private javafx.scene.layout.StackPane rootPane;
    @FXML private javafx.scene.layout.Region    bgImage;

    private final GameService svc = new GameService();
    private Timeline poller;
    private boolean iAmHost = false;

    @FXML
    public void initialize() {
        if (bgImage != null) ThemeService.applyCampfire(bgImage);
        startBtn.setDisable(true);
        gameLabel.setText("Partie #" + Session.currentGameId);
        poll();
        poller = new Timeline(new KeyFrame(Duration.seconds(2), e -> poll()));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
    }

    @FXML
    private void onStart() {
        startBtn.setDisable(true);
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception { svc.start(Session.currentGameId); return null; }
        };
        t.setOnFailed(e -> Platform.runLater(() -> {
            startBtn.setDisable(false);
            errorLabel.setText(t.getException().getMessage());
        }));
        new Thread(t).start();
    }

    @FXML
    private void onLeave() {
        stop();
        Router.go("lobby.fxml");
    }

    private void poll() {
        Task<StateResponse> t = new Task<>() {
            @Override protected StateResponse call() throws Exception { return svc.state(Session.currentGameId); }
        };
        t.setOnSucceeded(e -> {
            StateResponse s = t.getValue();
            playerList.setItems(FXCollections.observableArrayList(s.players));

            iAmHost = s.players.stream().anyMatch(p -> p.id == Session.playerId && p.host());
            startBtn.setDisable(!iAmHost || s.players.size() < 4);
            startBtn.setText(iAmHost
                    ? (s.players.size() < 4 ? "En attente (4 joueurs minimum)" : "Démarrer la partie")
                    : "En attente de l'hôte...");

            if (countLabel != null) {
                countLabel.setText(s.players.size() + " joueur" + (s.players.size() > 1 ? "s" : ""));
            }

            if (!"WAITING".equals(s.session.status)) {
                stop();
                Router.go("game.fxml");
            }
        });
        t.setOnFailed(e -> errorLabel.setText("Erreur réseau"));
        new Thread(t).start();
    }

    private void stop() { if (poller != null) poller.stop(); }
}
