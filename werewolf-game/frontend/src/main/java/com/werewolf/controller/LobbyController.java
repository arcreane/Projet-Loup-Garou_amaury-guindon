package com.werewolf.controller;

import com.werewolf.Router;
import com.werewolf.model.GameSession;
import com.werewolf.model.Invitation;
import com.werewolf.service.GameService;
import com.werewolf.service.Session;
import com.werewolf.service.SocialService;
import com.werewolf.service.ThemeService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;

import java.util.List;

public class LobbyController {

    @FXML private Label              welcomeLabel;
    @FXML private Label              eloLabel;
    @FXML private Label              gamesCountLabel;
    @FXML private ListView<GameSession> gameList;
    @FXML private TextField          newGameName;
    @FXML private CheckBox           rankedCheck;
    @FXML private Button             refreshBtn;
    @FXML private Button             createBtn;
    @FXML private Button             joinBtn;
    @FXML private Button             historyBtn;
    @FXML private Button             profileBtn;
    @FXML private Button             invitationsBtn;
    @FXML private Button             practiceBtn;
    @FXML private ComboBox<Integer>  practiceSizeCombo;
    @FXML private Label              errorLabel;
    @FXML private javafx.scene.layout.StackPane rootPane;
    @FXML private javafx.scene.layout.Region    bgImage;

    private final GameService svc = new GameService();
    private final SocialService social = new SocialService();
    private Timeline poller;

    @FXML
    public void initialize() {
        if (bgImage != null) ThemeService.applyTavern(bgImage);
        welcomeLabel.setText(Session.avatarUrl + "  " + Session.fullTag());
        eloLabel.setText("⚔  " + Session.elo + " ELO");

        if (practiceSizeCombo != null) {
            practiceSizeCombo.setItems(FXCollections.observableArrayList(4, 5, 6, 7, 8));
            practiceSizeCombo.setValue(5);
        }

        Label empty = new Label("Aucune partie en attente.\nCréez-en une ou lancez un entraînement.");
        empty.setStyle("-fx-text-fill: -text-muted; -fx-font-size: 14px; -fx-text-alignment: center;");
        gameList.setPlaceholder(empty);

        refresh();
        refreshInvitations();
        poller = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            refresh();
            refreshInvitations();
        }));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
    }

    @FXML private void onProfile() { stopPolling(); Router.go("profile.fxml"); }

    @FXML
    private void onPractice() {
        int total = practiceSizeCombo.getValue() == null ? 5 : practiceSizeCombo.getValue();
        practiceBtn.setDisable(true);
        runAsync(() -> {
            int sid = svc.createPracticeGame(total);
            Session.currentGameId = sid;
            Platform.runLater(() -> {
                stopPolling();
                Router.go("game.fxml");   // démarrage direct, pas de WaitingRoom
            });
        });
    }

    @FXML
    private void onInvitations() {
        Task<List<Invitation>> t = new Task<>() {
            @Override protected List<Invitation> call() throws Exception { return social.listInvitations(); }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> showInvitationsDialog(t.getValue())));
        t.setOnFailed(e -> errorLabel.setText(t.getException().getMessage()));
        new Thread(t).start();
    }

    private void refreshInvitations() {
        Task<List<Invitation>> t = new Task<>() {
            @Override protected List<Invitation> call() throws Exception { return social.listInvitations(); }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> {
            int n = t.getValue().size();
            invitationsBtn.setText(n > 0 ? ("📨  " + n) : "📨");
        }));
        new Thread(t).start();
    }

    private void showInvitationsDialog(List<Invitation> invitations) {
        if (invitations.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Invitations");
            a.setHeaderText("Aucune invitation");
            a.setContentText("Personne ne vous a invité dans une partie.");
            a.showAndWait();
            return;
        }
        ListView<Invitation> lv = new ListView<>(FXCollections.observableArrayList(invitations));
        lv.setPrefHeight(220);
        lv.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Invitation i, boolean empty) {
                super.updateItem(i, empty);
                setText(empty || i == null ? null
                        : i.fromTag() + "  →  " + i.sessionName + (i.ranked() ? "  🏆" : ""));
            }
        });
        lv.getSelectionModel().select(0);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Invitations reçues");
        dlg.setHeaderText("Sélectionnez puis choisissez l'action");
        dlg.getDialogPane().setContent(lv);

        ButtonType accept  = new ButtonType("Accepter",  ButtonBar.ButtonData.OK_DONE);
        ButtonType decline = new ButtonType("Refuser",   ButtonBar.ButtonData.NO);
        ButtonType cancel  = new ButtonType("Fermer",    ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().setAll(accept, decline, cancel);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return;

        Invitation selected = lv.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (result.get() == accept) {
            runAsync(() -> {
                int sid = social.acceptInvitation(selected.id);
                Session.currentGameId = sid;
                Platform.runLater(() -> { stopPolling(); Router.go("waiting_room.fxml"); });
            });
        } else if (result.get() == decline) {
            runAsync(() -> {
                social.declineInvitation(selected.id);
                Platform.runLater(this::refreshInvitations);
            });
        }
    }

    @FXML private void onRefresh() { refresh(); }

    @FXML private void onHistory() {
        stopPolling();
        Router.go("history.fxml");
    }

    @FXML
    private void onCreate() {
        String name = newGameName.getText().trim();
        if (name.isEmpty()) name = "Partie de " + Session.pseudo;
        boolean ranked = rankedCheck.isSelected();
        final String fname = name;
        runAsync(() -> {
            int sid = svc.createGame(fname, ranked);
            svc.joinGame(sid);
            Session.currentGameId = sid;
            Platform.runLater(() -> {
                stopPolling();
                Router.go("waiting_room.fxml");
            });
        });
    }

    @FXML
    private void onJoin() {
        GameSession g = gameList.getSelectionModel().getSelectedItem();
        if (g == null) { errorLabel.setText("Sélectionnez une partie."); return; }
        runAsync(() -> {
            svc.joinGame(g.id);
            Session.currentGameId = g.id;
            Platform.runLater(() -> {
                stopPolling();
                Router.go("waiting_room.fxml");
            });
        });
    }

    private void refresh() {
        Task<List<GameSession>> t = new Task<>() {
            @Override protected List<GameSession> call() throws Exception { return svc.listGames(); }
        };
        t.setOnSucceeded(e -> {
            List<GameSession> games = t.getValue();
            GameSession sel = gameList.getSelectionModel().getSelectedItem();
            Integer selectedId = sel == null ? null : sel.id;

            var currentIds = gameList.getItems().stream().map(g -> g.id).toList();
            var newIds = games.stream().map(g -> g.id).toList();
            if (!currentIds.equals(newIds)) {
                gameList.setItems(FXCollections.observableArrayList(games));
                if (selectedId != null) {
                    for (GameSession g : games) {
                        if (g.id == selectedId) { gameList.getSelectionModel().select(g); break; }
                    }
                }
            }
            if (gamesCountLabel != null) {
                gamesCountLabel.setText(games.size() == 0 ? "" : games.size() + " disponible" + (games.size() > 1 ? "s" : ""));
            }
        });
        t.setOnFailed(e -> errorLabel.setText("Erreur réseau"));
        new Thread(t, "lobby-refresh").start();
    }

    private void runAsync(ThrowingRunnable r) {
        errorLabel.setText("");
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception { r.run(); return null; }
        };
        t.setOnFailed(e -> {
            Throwable ex = t.getException();
            errorLabel.setText(ex == null ? "Erreur" : ex.getMessage());
        });
        new Thread(t).start();
    }

    private void stopPolling() { if (poller != null) poller.stop(); }

    interface ThrowingRunnable { void run() throws Exception; }
}
