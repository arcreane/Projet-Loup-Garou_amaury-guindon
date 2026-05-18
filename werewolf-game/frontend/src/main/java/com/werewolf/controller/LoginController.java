package com.werewolf.controller;

import com.werewolf.Router;
import com.werewolf.service.AuthService;
import com.werewolf.service.GameService;
import com.werewolf.service.Session;
import com.werewolf.service.ThemeService;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.List;

public class LoginController {

    private static final List<String> AVATARS = List.of(
            "👤","🧙","🐺","👁","🧪","🏹","🌕","🦉","🦊","🦇","🕯","🗡"
    );

    @FXML private TextField     pseudoField;
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> avatarCombo;
    @FXML private Button        loginBtn;
    @FXML private Button        registerBtn;
    @FXML private Label         errorLabel;
    @FXML private Region        moonDeco;
    @FXML private StackPane     rootPane;
    @FXML private Region        bgImage;

    private final AuthService auth = new AuthService();
    private final GameService svc  = new GameService();

    @FXML
    public void initialize() {
        avatarCombo.setItems(FXCollections.observableArrayList(AVATARS));
        avatarCombo.setValue("👤");

        if (bgImage != null) ThemeService.applyNight(bgImage);

        if (moonDeco != null) {
            Circle clip = new Circle();
            clip.centerXProperty().bind(Bindings.divide(moonDeco.widthProperty(), 2));
            clip.centerYProperty().bind(Bindings.divide(moonDeco.heightProperty(), 2));
            clip.radiusProperty().bind(Bindings.divide(
                    Bindings.min(moonDeco.widthProperty(), moonDeco.heightProperty()), 2));
            moonDeco.setClip(clip);

            TranslateTransition tt = new TranslateTransition(Duration.seconds(5), moonDeco);
            tt.setFromY(0);
            tt.setToY(-10);
            tt.setAutoReverse(true);
            tt.setCycleCount(TranslateTransition.INDEFINITE);
            tt.setInterpolator(Interpolator.EASE_BOTH);
            tt.play();
        }
    }

    @FXML private void onLogin()    { run(false); }
    @FXML private void onRegister() { run(true);  }

    private void run(boolean register) {
        errorLabel.setText("");
        loginBtn.setDisable(true);
        registerBtn.setDisable(true);

        String pseudo = pseudoField.getText().trim();
        String email  = emailField.getText().trim();
        String pass   = passwordField.getText();
        String avatar = avatarCombo.getValue();

        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception {
                if (register) {
                    auth.register(pseudo, email, pass);
                    if (avatar != null && !avatar.equals("👤")) {
                        svc.updateAvatar(avatar);
                        Session.avatarUrl = avatar;
                    }
                } else {
                    auth.login(pseudo, pass);
                }
                return null;
            }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> Router.go("lobby.fxml")));
        t.setOnFailed(e -> Platform.runLater(() -> {
            loginBtn.setDisable(false);
            registerBtn.setDisable(false);
            Throwable ex = t.getException();
            errorLabel.setText(ex == null ? "Erreur" : ex.getMessage());
        }));
        new Thread(t, "auth").start();
    }
}
