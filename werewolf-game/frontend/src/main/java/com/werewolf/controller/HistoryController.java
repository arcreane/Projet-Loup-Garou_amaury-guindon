package com.werewolf.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.werewolf.Router;
import com.werewolf.model.HistoryEntry;
import com.werewolf.model.Role;
import com.werewolf.service.GameService;
import com.werewolf.service.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

public class HistoryController {

    @FXML private ListView<String> historyList;
    @FXML private ListView<String> leaderboardList;
    @FXML private Label            myStatsLabel;
    @FXML private Button           backBtn;

    private final GameService svc = new GameService();

    @FXML
    public void initialize() {
        myStatsLabel.setText(Session.avatarUrl + "  " + Session.pseudo
                + "   ·   ELO " + Session.elo
                + "   ·   " + Session.gamesWon + " victoires / " + Session.gamesPlayed + " parties");
        loadHistory();
        loadLeaderboard();
    }

    @FXML
    private void onBack() { Router.go("lobby.fxml"); }

    private void loadHistory() {
        Task<List<HistoryEntry>> t = new Task<>() {
            @Override protected List<HistoryEntry> call() throws Exception { return svc.history(); }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> {
            List<String> lines = new ArrayList<>();
            for (HistoryEntry h : t.getValue()) {
                Role r = Role.parse(h.role);
                String result = h.iWon() ? "✅ Victoire" : "❌ Défaite";
                String roleLabel = (r == null ? "?" : r.label());
                StringBuilder sb = new StringBuilder();
                sb.append(h.ranked() ? "🏆 " : "  ");
                sb.append(h.name).append("  ·  ").append(roleLabel).append("  ·  ").append(result);
                Integer d = h.eloDelta();
                if (h.ranked() && d != null) {
                    sb.append("  (").append(d >= 0 ? "+" : "").append(d).append(" ELO)");
                }
                lines.add(sb.toString());
            }
            if (lines.isEmpty()) lines.add("(Aucune partie terminée)");
            historyList.setItems(FXCollections.observableArrayList(lines));
        }));
        new Thread(t).start();
    }

    private void loadLeaderboard() {
        Task<JsonNode> t = new Task<>() {
            @Override protected JsonNode call() throws Exception { return svc.leaderboard(); }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> {
            JsonNode board = t.getValue().path("leaderboard");
            List<String> lines = new ArrayList<>();
            int rank = 1;
            for (JsonNode n : board) {
                String av = n.path("avatar_url").asText("");
                if (av.isEmpty()) av = "👤";
                String medal = switch (rank) { case 1 -> "🥇"; case 2 -> "🥈"; case 3 -> "🥉"; default -> "  "; };
                lines.add(String.format("%s  #%-2d  %s %-15s   %4d ELO   (%d/%d)",
                        medal, rank, av, n.path("pseudo").asText(""),
                        n.path("elo").asInt(),
                        n.path("games_won").asInt(),
                        n.path("games_played").asInt()));
                rank++;
            }
            if (lines.isEmpty()) lines.add("(Aucun joueur classé)");
            leaderboardList.setItems(FXCollections.observableArrayList(lines));
        }));
        new Thread(t).start();
    }
}
