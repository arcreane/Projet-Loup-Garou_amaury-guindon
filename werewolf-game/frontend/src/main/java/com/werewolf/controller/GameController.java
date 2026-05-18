package com.werewolf.controller;

import com.werewolf.Router;
import com.werewolf.model.ChatMessage;
import com.werewolf.model.Phase;
import com.werewolf.model.Player;
import com.werewolf.model.Role;
import com.werewolf.model.StateResponse;
import com.werewolf.service.GameService;
import com.werewolf.service.Session;
import com.werewolf.service.SoundPlayer;
import com.werewolf.service.ThemeService;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;

/**
 * Écran de jeu principal. Inclut :
 * - Panneau d'action contextuel selon phase + rôle
 * - Chat (canal village + canal loups)
 * - Dialogue de tir interactif du Chasseur
 * - Mode spectateur pour les morts (voient tous les rôles)
 * - Sons sur changement de phase
 */
public class GameController {

    @FXML private Label        phaseLabel;
    @FXML private Label        roleLabel;
    @FXML private Label        roundLabel;
    @FXML private Label        rankedLabel;
    @FXML private ProgressBar  phaseTimer;
    @FXML private Label        timerLabel;

    @FXML private TreeView<String> playersTree;
    @FXML private ListView<String> logsView;

    @FXML private Label              actionTitle;
    @FXML private ListView<Player>   targetList;
    @FXML private ComboBox<Player>   targetCombo;
    @FXML private Button             primaryActionBtn;
    @FXML private Button             secondaryActionBtn;
    @FXML private Button             passBtn;
    @FXML private Label              actionHint;

    // Chat
    @FXML private ListView<String>   chatView;
    @FXML private TextField          chatField;
    @FXML private ComboBox<String>   chatScope;
    @FXML private Button             chatSendBtn;

    // Décors dynamiques (fond + lune/soleil)
    @FXML private Region             dynamicBg;
    @FXML private Region             skyBody;

    private final GameService svc = new GameService();
    private Timeline poller;

    private String lastPhase = null;
    private int    lastRound = -1;
    private int    lastAliveCount = -1;
    private String lastStatus = null;
    private boolean hunterDialogOpen = false;

    private StateResponse currentState;
    private int localTimer = 0;

    @FXML
    public void initialize() {
        // Le fond dynamique doit remplir tout l'écran
        if (dynamicBg != null) {
            dynamicBg.setOpacity(0);
            dynamicBg.prefWidthProperty().bind(((javafx.scene.layout.StackPane) dynamicBg.getParent()).widthProperty());
            dynamicBg.prefHeightProperty().bind(((javafx.scene.layout.StackPane) dynamicBg.getParent()).heightProperty());
        }
        if (skyBody != null) skyBody.setOpacity(0);

        targetList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Player p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.avatar() + " " + p.pseudo);
            }
        });
        targetCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Player p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.avatar() + " " + p.pseudo);
            }
        });
        targetCombo.setButtonCell(targetCombo.getCellFactory().call(null));

        chatScope.setItems(FXCollections.observableArrayList("Village", "Loups"));
        chatScope.setValue("Village");

        chatField.setOnAction(e -> onSendChat());

        poll();
        poller = new Timeline(new KeyFrame(Duration.seconds(2), e -> poll()));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();

        Timeline tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickTimer()));
        tick.setCycleCount(Timeline.INDEFINITE);
        tick.play();
    }

    // =====================================================
    //                       POLLING
    // =====================================================
    private void poll() {
        Task<StateResponse> t = new Task<>() {
            @Override protected StateResponse call() throws Exception { return svc.state(Session.currentGameId); }
        };
        t.setOnSucceeded(e -> applyState(t.getValue()));
        new Thread(t).start();
    }

    private void applyState(StateResponse s) {
        currentState = s;

        Role myRole = s.myRoleEnum();
        roleLabel.setText("Vous êtes : " + (myRole == null ? "?" : myRole.label())
                + (s.iAmDead ? "  (esprit)" : ""));
        phaseLabel.setText(Phase.prettyName(s.session.phase));
        roundLabel.setText("Round " + s.session.round);
        rankedLabel.setText(s.session.ranked() ? "🏆 Classée" : "Amicale");
        rankedLabel.getStyleClass().setAll("badge", s.session.ranked() ? "badge-ranked" : "badge-casual");

        // Logs
        List<String> lines = s.logs.stream().map(l -> "• " + l.message).toList();
        if (lines.size() != logsView.getItems().size()) {
            logsView.setItems(FXCollections.observableArrayList(lines));
            if (!lines.isEmpty()) logsView.scrollTo(lines.size() - 1);
        }

        // Chat
        if (s.chat != null) {
            List<String> chatLines = s.chat.stream().map(this::formatChat).toList();
            if (chatLines.size() != chatView.getItems().size()) {
                chatView.setItems(FXCollections.observableArrayList(chatLines));
                if (!chatLines.isEmpty()) chatView.scrollTo(chatLines.size() - 1);
            }
        }

        // Filtrage du canal "Loups" selon rôle
        boolean canWolfChat = myRole == Role.WEREWOLF;
        if (!canWolfChat && "Loups".equals(chatScope.getValue())) chatScope.setValue("Village");
        chatScope.getItems().setAll(canWolfChat ? List.of("Village","Loups") : List.of("Village"));

        // Arbre joueurs
        TreeItem<String> root = new TreeItem<>("Joueurs");
        TreeItem<String> alive = new TreeItem<>("Vivants");
        TreeItem<String> dead  = new TreeItem<>("Éliminés");
        for (Player p : s.players) {
            String roleSuffix = (p.role != null) ? " — " + Role.parse(p.role).label() : "";
            String meTag = (p.id == Session.playerId) ? " (vous)" : "";
            String hostTag = p.host() ? " 👑" : "";
            String label = p.avatar() + " " + p.pseudo + hostTag + meTag + roleSuffix;
            (p.alive() ? alive : dead).getChildren().add(new TreeItem<>(label));
        }
        alive.setExpanded(true);
        dead.setExpanded(true);
        root.getChildren().addAll(alive, dead);
        root.setExpanded(true);
        playersTree.setRoot(root);
        playersTree.setShowRoot(false);

        buildActionPanel(s);

        // Sons + transitions visuelles
        if (!Objects.equals(lastStatus, s.session.status)) {
            applyDayNightTheme(s.session.status);
        }
        if (!Objects.equals(lastPhase, s.session.phase)) {
            if ("NIGHT".equals(s.session.status)) SoundPlayer.play("night");
            if ("DAY".equals(s.session.status))   SoundPlayer.play("day");
            if (Phase.NIGHT_WEREWOLF.equals(s.session.phase)) SoundPlayer.play("howl");
            flash(phaseLabel);
        }

        int aliveCount = (int) s.players.stream().filter(Player::alive).count();
        if (lastAliveCount != -1 && aliveCount < lastAliveCount) {
            SoundPlayer.play("death");
            flash(playersTree);
        }

        // Chasseur : si c'est MON id qui est pending, j'ouvre le dialog
        if (s.session.pendingHunterId != null
                && s.session.pendingHunterId == Session.playerId
                && !hunterDialogOpen) {
            openHunterDialog(s);
        }

        if (!Objects.equals(lastStatus, s.session.status) && "ENDED".equals(s.session.status)) {
            SoundPlayer.play("victory");
            showEndGame(s);
            if (poller != null) poller.stop();
        }
        lastPhase = s.session.phase;
        lastRound = s.session.round;
        lastAliveCount = aliveCount;
        lastStatus = s.session.status;
    }

    private String formatChat(ChatMessage m) {
        String prefix = "WEREWOLVES".equals(m.scope) ? "🐺 " : "";
        String av = (m.avatarUrl == null || m.avatarUrl.isEmpty()) ? "👤" : m.avatarUrl;
        return prefix + av + " " + m.pseudo + " : " + m.message;
    }

    private void tickTimer() {
        if (currentState == null) return;
        if (currentState.session.timer != localTimer) localTimer = currentState.session.timer;
        int dur = Math.max(1, currentState.session.phaseDuration);
        double pct = Math.max(0, Math.min(1.0, (double) localTimer / dur));
        phaseTimer.setProgress(pct);
        timerLabel.setText(localTimer + " s");
        if (localTimer > 0) localTimer--;
    }

    // =====================================================
    //                    PANNEAU D'ACTION
    // =====================================================
    private void buildActionPanel(StateResponse s) {
        Role myRole = s.myRoleEnum();
        String phase = s.session.phase;
        boolean iAmAlive = !s.iAmDead;

        targetList.setVisible(false);  targetList.setManaged(false);
        targetCombo.setVisible(false); targetCombo.setManaged(false);
        secondaryActionBtn.setVisible(false); secondaryActionBtn.setManaged(false);
        passBtn.setVisible(false);     passBtn.setManaged(false);
        primaryActionBtn.setVisible(true); primaryActionBtn.setManaged(true);
        primaryActionBtn.setDisable(false);
        primaryActionBtn.setOnAction(null);
        secondaryActionBtn.setOnAction(null);
        passBtn.setOnAction(null);

        if (!iAmAlive) {
            actionTitle.setText("👻 Mode spectateur");
            actionHint.setText("Vous voyez désormais tous les rôles. Profitez du spectacle !");
            primaryActionBtn.setVisible(false); primaryActionBtn.setManaged(false);
            return;
        }
        if ("ENDED".equals(s.session.status)) {
            actionTitle.setText("Partie terminée");
            actionHint.setText(s.session.winnerTeam == null ? "" : "Vainqueur : " + s.session.winnerTeam);
            primaryActionBtn.setVisible(false); primaryActionBtn.setManaged(false);
            return;
        }

        var others   = s.players.stream().filter(p -> p.alive() && p.id != Session.playerId).toList();
        var allAlive = s.players.stream().filter(Player::alive).toList();

        if (Phase.NIGHT_WEREWOLF.equals(phase) && myRole == Role.WEREWOLF) {
            actionTitle.setText("Choisissez votre victime");
            actionHint.setText("Les loups votent ensemble. Majorité l'emporte.");
            showTargetList(others);
            primaryActionBtn.setText("Voter");
            primaryActionBtn.setOnAction(e -> doVote());
        }
        else if (Phase.NIGHT_SEER.equals(phase) && myRole == Role.SEER) {
            actionTitle.setText("Sondez un joueur");
            actionHint.setText("Vous découvrirez son rôle.");
            showTargetCombo(others);
            primaryActionBtn.setText("Révéler");
            primaryActionBtn.setOnAction(e -> doSeerReveal());
        }
        else if (Phase.NIGHT_WITCH.equals(phase) && myRole == Role.WITCH) {
            actionTitle.setText("Sorcière, agissez");
            actionHint.setText("Sauvez la victime ou tuez un joueur (1 fois chacun).");
            showTargetCombo(allAlive);
            primaryActionBtn.setText("Sauver la victime");
            primaryActionBtn.setOnAction(e -> doWitch("WITCH_HEAL", null));
            secondaryActionBtn.setText("Empoisonner la cible");
            secondaryActionBtn.setVisible(true); secondaryActionBtn.setManaged(true);
            secondaryActionBtn.setOnAction(e -> doWitch("WITCH_KILL", targetCombo.getValue()));
            passBtn.setText("Ne rien faire");
            passBtn.setVisible(true); passBtn.setManaged(true);
            passBtn.setOnAction(e -> doWitch("WITCH_PASS", null));
        }
        else if (Phase.DAY_VOTE.equals(phase)) {
            actionTitle.setText("Vote du village");
            actionHint.setText("Qui doit être éliminé ?");
            showTargetList(others);
            primaryActionBtn.setText("Voter");
            primaryActionBtn.setOnAction(e -> doVote());
        }
        else {
            actionTitle.setText("En attente");
            actionHint.setText("Aucune action pour vous dans cette phase. Patientez...");
            primaryActionBtn.setVisible(false); primaryActionBtn.setManaged(false);
        }
    }

    private void showTargetList(List<Player> opts) {
        Player sel = targetList.getSelectionModel().getSelectedItem();
        Integer selId = sel == null ? null : sel.id;
        var currentIds = targetList.getItems().stream().map(p -> p.id).toList();
        var newIds = opts.stream().map(p -> p.id).toList();
        if (!currentIds.equals(newIds)) {
            targetList.setItems(FXCollections.observableArrayList(opts));
            if (selId != null) for (Player p : opts) if (p.id == selId) { targetList.getSelectionModel().select(p); break; }
        }
        targetList.setVisible(true); targetList.setManaged(true);
    }

    private void showTargetCombo(List<Player> opts) {
        Player sel = targetCombo.getValue();
        Integer selId = sel == null ? null : sel.id;
        var currentIds = targetCombo.getItems().stream().map(p -> p.id).toList();
        var newIds = opts.stream().map(p -> p.id).toList();
        if (!currentIds.equals(newIds)) {
            targetCombo.setItems(FXCollections.observableArrayList(opts));
            if (selId != null) for (Player p : opts) if (p.id == selId) { targetCombo.setValue(p); break; }
        }
        targetCombo.setVisible(true); targetCombo.setManaged(true);
    }

    // =====================================================
    //                       ACTIONS
    // =====================================================
    private void doVote() {
        Player target = targetList.getSelectionModel().getSelectedItem();
        if (target == null) { actionHint.setText("Sélectionnez une cible."); return; }
        primaryActionBtn.setDisable(true);
        runAsync(() -> svc.vote(Session.currentGameId, target.id), "Vote enregistré pour " + target.pseudo);
    }

    private void doSeerReveal() {
        Player target = targetCombo.getValue();
        if (target == null) { actionHint.setText("Choisissez une cible."); return; }
        primaryActionBtn.setDisable(true);
        Task<com.fasterxml.jackson.databind.JsonNode> t = new Task<>() {
            @Override protected com.fasterxml.jackson.databind.JsonNode call() throws Exception {
                return svc.nightAction(Session.currentGameId, "SEER_REVEAL", target.id);
            }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> {
            String role = t.getValue().path("role").asText();
            Role r = Role.parse(role);
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Vision de la Voyante");
            a.setHeaderText(target.pseudo);
            a.setContentText(r == null ? "Rôle inconnu." : "Cette personne est : " + r.label());
            // Ajoute l'illustration du rôle si disponible
            javafx.scene.image.Image img = (r == null) ? null : ThemeService.roleImage(r.name());
            if (img != null) {
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                iv.setFitWidth(180); iv.setPreserveRatio(true);
                a.setGraphic(iv);
            }
            a.showAndWait();
        }));
        t.setOnFailed(e -> Platform.runLater(() -> actionHint.setText(t.getException().getMessage())));
        new Thread(t).start();
    }

    private void doWitch(String type, Player target) {
        Integer tid = target == null ? null : target.id;
        primaryActionBtn.setDisable(true);
        secondaryActionBtn.setDisable(true);
        passBtn.setDisable(true);
        runAsync(() -> svc.nightAction(Session.currentGameId, type, tid), "Action de sorcière enregistrée");
    }

    // =====================================================
    //                CHAT
    // =====================================================
    @FXML
    private void onSendChat() {
        String msg = chatField.getText().trim();
        if (msg.isEmpty()) return;
        String scope = "Loups".equals(chatScope.getValue()) ? "WEREWOLVES" : "ALL";
        chatField.clear();
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception {
                svc.sendChat(Session.currentGameId, msg, scope);
                return null;
            }
        };
        new Thread(t).start();
    }

    // =====================================================
    //         CHASSEUR : dialogue interactif
    // =====================================================
    private void openHunterDialog(StateResponse s) {
        hunterDialogOpen = true;
        ChoiceDialog<Player> dlg = new ChoiceDialog<>();
        dlg.getItems().setAll(s.players.stream()
                .filter(p -> p.alive() && p.id != Session.playerId).toList());
        dlg.setTitle("Dernière flèche du Chasseur");
        dlg.setHeaderText("Vous êtes mort. Choisissez qui emporter avec vous.");
        dlg.setContentText("Cible :");
        // Convertit pour afficher les pseudos correctement
        Platform.runLater(() -> {
            dlg.showAndWait().ifPresent(target -> {
                Task<Void> t = new Task<>() {
                    @Override protected Void call() throws Exception {
                        svc.nightAction(Session.currentGameId, "HUNTER_SHOT", target.id);
                        return null;
                    }
                };
                t.setOnSucceeded(e -> hunterDialogOpen = false);
                t.setOnFailed(e -> hunterDialogOpen = false);
                new Thread(t).start();
            });
            // Si l'utilisateur ferme sans choisir, on remet le flag pour pouvoir réouvrir
            hunterDialogOpen = false;
        });
    }

    // =====================================================
    //                FIN DE PARTIE
    // =====================================================
    private void showEndGame(StateResponse s) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Fin de la partie");
        String winner = s.session.winnerTeam == null ? "?"
                : ("VILLAGERS".equals(s.session.winnerTeam)
                    ? "⚔ Les Villageois triomphent !"
                    : "🐺 Les Loups-Garous dévorent le village");
        a.setHeaderText(winner);
        StringBuilder sb = new StringBuilder();
        sb.append("Rôles révélés :\n");
        for (Player p : s.players) {
            Role r = Role.parse(p.role);
            sb.append(" • ").append(p.pseudo).append(" : ").append(r == null ? "?" : r.label());
            if (s.session.ranked() && p.eloBefore != null && p.eloAfter != null) {
                int delta = p.eloAfter - p.eloBefore;
                sb.append("  (").append(p.eloBefore).append(" → ").append(p.eloAfter)
                  .append(", ").append(delta >= 0 ? "+" : "").append(delta).append(')');
            }
            sb.append('\n');
        }
        a.setContentText(sb.toString());

        // Image du camp vainqueur
        String camp = "VILLAGERS".equals(s.session.winnerTeam) ? "villager" : "werewolf";
        javafx.scene.image.Image img = ThemeService.roleImage(camp.toUpperCase());
        if (img != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
            iv.setFitWidth(220); iv.setPreserveRatio(true);
            a.setGraphic(iv);
        }
        a.setOnHidden(e -> {
            // Refresh ELO local depuis /me
            new Thread(() -> {
                try { new com.werewolf.service.AuthService().refreshProfile(); } catch (Exception ignored) {}
                Platform.runLater(() -> Router.go("lobby.fxml"));
            }).start();
        });
        a.show();
    }

    private void runAsync(ThrowingRunnable r, String okMsg) {
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception { r.run(); return null; }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> actionHint.setText(okMsg)));
        t.setOnFailed(e -> Platform.runLater(() -> {
            actionHint.setText(t.getException().getMessage());
            primaryActionBtn.setDisable(false);
            secondaryActionBtn.setDisable(false);
            passBtn.setDisable(false);
        }));
        new Thread(t).start();
    }

    private void flash(javafx.scene.Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setFromValue(0.2);
        ft.setToValue(1.0);
        ft.play();
    }

    // =====================================================
    //              ANIMATIONS NUIT / JOUR
    // =====================================================
    private void applyDayNightTheme(String status) {
        boolean isDay = "DAY".equals(status);

        // Background image dynamique
        if (dynamicBg != null) {
            ThemeService.applyBackground(dynamicBg,
                    isDay ? "backgrounds/day.jpg" : "backgrounds/night.jpg",
                    true);
            FadeTransition ft = new FadeTransition(Duration.seconds(1.2), dynamicBg);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        }

        // Astre céleste : lune ou soleil + animation d'apparition
        if (skyBody != null) {
            skyBody.getStyleClass().removeAll("moon", "sun");
            skyBody.getStyleClass().add(isDay ? "sun" : "moon");

            skyBody.setTranslateY(120);
            skyBody.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.seconds(1.8), skyBody);
            fade.setFromValue(0);
            fade.setToValue(1);
            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(Duration.seconds(1.8), skyBody);
            tt.setFromY(120);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition st = new ScaleTransition(Duration.seconds(1.0), skyBody);
            st.setFromX(0.5); st.setFromY(0.5);
            st.setToX(1.0); st.setToY(1.0);
            fade.play();
            tt.play();
            st.play();
        }
    }

    interface ThrowingRunnable { void run() throws Exception; }
}
