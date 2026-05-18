package com.werewolf.controller;

import com.werewolf.Router;
import com.werewolf.model.Friend;
import com.werewolf.model.FriendRequest;
import com.werewolf.model.FriendsResponse;
import com.werewolf.service.AuthService;
import com.werewolf.service.GameService;
import com.werewolf.service.Session;
import com.werewolf.service.SocialService;
import com.werewolf.service.ThemeService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.util.List;

/**
 * Page profil + amis :
 * - Colonne gauche : édition avatar / email / mot de passe
 * - Colonne droite : ajout d'ami + onglets (Amis, Demandes reçues, Demandes envoyées)
 */
public class ProfileController {

    private static final List<String> AVATARS = List.of(
            "👤","🧙","🐺","👁","🧪","🏹","🌕","🦉","🦊","🦇","🕯","🗡"
    );

    @FXML private BorderPane    rootPane;
    @FXML private Label         myTagLabel;

    // Profil
    @FXML private ComboBox<String> avatarCombo;
    @FXML private TextField        emailField;
    @FXML private PasswordField    oldPwdField;
    @FXML private PasswordField    newPwdField;
    @FXML private PasswordField    newPwdConfirmField;
    @FXML private Label            profileMsgLabel;

    // Amis
    @FXML private TextField        addFriendField;
    @FXML private Label            addFriendMsgLabel;
    @FXML private ListView<Friend>         friendsList;
    @FXML private ListView<FriendRequest>  receivedList;
    @FXML private ListView<FriendRequest>  sentList;

    private final SocialService social = new SocialService();
    private final AuthService   auth   = new AuthService();

    @FXML
    public void initialize() {
        if (rootPane != null) ThemeService.applyParchment(rootPane);

        myTagLabel.setText(Session.avatarUrl + "  " + Session.fullTag());

        avatarCombo.setItems(FXCollections.observableArrayList(AVATARS));
        avatarCombo.setValue(Session.avatarUrl);
        emailField.setText(Session.email);

        // Cell factories pour afficher les amis correctement
        friendsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Friend f, boolean empty) {
                super.updateItem(f, empty);
                if (empty || f == null) { setText(null); return; }
                setText(f.statusIcon() + "  " + f.fullTag()
                        + "   ·   " + f.elo + " ELO"
                        + "   ·   " + f.gamesWon + "/" + f.gamesPlayed + " V");
            }
        });
        receivedList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(FriendRequest r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.fullTag());
            }
        });
        sentList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(FriendRequest r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.fullTag() + "  (en attente)");
            }
        });

        friendsList.setPlaceholder(new Label("Aucun ami pour l'instant. Envoyez une demande !"));
        receivedList.setPlaceholder(new Label("Aucune demande reçue."));
        sentList.setPlaceholder(new Label("Aucune demande envoyée."));

        loadFriends();
    }

    @FXML private void onBack() { Router.go("lobby.fxml"); }

    // =====================================================
    //                       PROFIL
    // =====================================================
    @FXML
    private void onUpdateAvatar() {
        String av = avatarCombo.getValue();
        runAsync(() -> {
            social.updateAvatar(av);
            Session.avatarUrl = av;
            Platform.runLater(() -> {
                myTagLabel.setText(Session.avatarUrl + "  " + Session.fullTag());
                profileMsgLabel.setText("Sceau mis à jour ✓");
            });
        });
    }

    @FXML
    private void onUpdateEmail() {
        String em = emailField.getText().trim();
        runAsync(() -> {
            social.updateEmail(em);
            Session.email = em;
            Platform.runLater(() -> profileMsgLabel.setText("Email mis à jour ✓"));
        });
    }

    @FXML
    private void onUpdatePassword() {
        String old = oldPwdField.getText();
        String n1  = newPwdField.getText();
        String n2  = newPwdConfirmField.getText();
        if (!n1.equals(n2)) { profileMsgLabel.setText("Les deux mots de passe ne correspondent pas."); return; }
        if (n1.length() < 4) { profileMsgLabel.setText("Mot de passe trop court (4 min)."); return; }
        runAsync(() -> {
            social.updatePassword(old, n1);
            Platform.runLater(() -> {
                oldPwdField.clear(); newPwdField.clear(); newPwdConfirmField.clear();
                profileMsgLabel.setText("Mot de passe changé ✓");
            });
        });
    }

    // =====================================================
    //                       AMIS
    // =====================================================
    private void loadFriends() {
        Task<FriendsResponse> t = new Task<>() {
            @Override protected FriendsResponse call() throws Exception { return social.listFriends(); }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> {
            FriendsResponse fr = t.getValue();
            friendsList.setItems(FXCollections.observableArrayList(fr.friends == null ? List.of() : fr.friends));
            receivedList.setItems(FXCollections.observableArrayList(fr.received == null ? List.of() : fr.received));
            sentList.setItems(FXCollections.observableArrayList(fr.sent == null ? List.of() : fr.sent));
        }));
        new Thread(t).start();
    }

    @FXML
    private void onSendRequest() {
        String tag = addFriendField.getText().trim();
        if (tag.isEmpty()) { addFriendMsgLabel.setText("Tapez pseudo#1234"); return; }
        runAsync(() -> {
            var r = social.sendFriendRequest(tag);
            Platform.runLater(() -> {
                if (r.path("auto_accepted").asBoolean(false)) {
                    addFriendMsgLabel.setText("Vous êtes maintenant amis ✓");
                } else if (r.path("already_friends").asBoolean(false)) {
                    addFriendMsgLabel.setText("Déjà ami avec ce joueur");
                } else {
                    addFriendMsgLabel.setText("Demande envoyée ✓");
                }
                addFriendField.clear();
                loadFriends();
            });
        }, addFriendMsgLabel);
    }

    @FXML
    private void onAcceptRequest() {
        FriendRequest r = receivedList.getSelectionModel().getSelectedItem();
        if (r == null) return;
        runAsync(() -> {
            social.acceptFriend(r.id);
            Platform.runLater(this::loadFriends);
        });
    }

    @FXML
    private void onDeclineRequest() {
        FriendRequest r = receivedList.getSelectionModel().getSelectedItem();
        if (r == null) return;
        runAsync(() -> {
            social.declineFriend(r.id);
            Platform.runLater(this::loadFriends);
        });
    }

    @FXML
    private void onRemoveFriend() {
        Friend f = friendsList.getSelectionModel().getSelectedItem();
        if (f == null) return;
        runAsync(() -> {
            social.removeFriend(f.id);
            Platform.runLater(this::loadFriends);
        });
    }

    @FXML
    private void onInviteFriend() {
        Friend f = friendsList.getSelectionModel().getSelectedItem();
        if (f == null) { profileMsgLabel.setText("Sélectionnez un ami"); return; }
        if (Session.currentGameId <= 0) {
            profileMsgLabel.setText("Vous n'êtes pas dans une partie en attente");
            return;
        }
        runAsync(() -> {
            social.sendInvitation(f.id, Session.currentGameId);
            Platform.runLater(() -> profileMsgLabel.setText("Invitation envoyée à " + f.pseudo + " ✓"));
        });
    }

    // =====================================================
    //                       HELPERS
    // =====================================================
    private void runAsync(ThrowingRunnable r) { runAsync(r, profileMsgLabel); }

    private void runAsync(ThrowingRunnable r, Label msgLabel) {
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception { r.run(); return null; }
        };
        t.setOnFailed(e -> Platform.runLater(() ->
            msgLabel.setText(t.getException().getMessage())));
        new Thread(t).start();
    }

    interface ThrowingRunnable { void run() throws Exception; }
}
