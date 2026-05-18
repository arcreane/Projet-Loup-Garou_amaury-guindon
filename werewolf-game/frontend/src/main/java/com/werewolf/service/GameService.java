package com.werewolf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.model.GameSession;
import com.werewolf.model.HistoryEntry;
import com.werewolf.model.StateResponse;

import java.util.List;

public class GameService {

    private final ApiClient api = new ApiClient();
    private static final ObjectMapper M = new ObjectMapper();

    public List<GameSession> listGames() throws Exception {
        JsonNode r = api.get("/games");
        return M.convertValue(r.path("games"), new TypeReference<List<GameSession>>() {});
    }

    public int createGame(String name, boolean ranked) throws Exception {
        JsonNode r = api.post("/games", ApiClient.body(
                "name", name,
                "is_ranked", ranked ? 1 : 0
        ));
        return r.path("session_id").asInt();
    }

    /** Crée une partie d'entraînement (le joueur + N-1 bots, démarre tout de suite). */
    public int createPracticeGame(int totalPlayers) throws Exception {
        JsonNode r = api.post("/games/practice", ApiClient.body("total_players", totalPlayers));
        return r.path("session_id").asInt();
    }

    public void joinGame(int id) throws Exception {
        api.post("/games/" + id + "/join", null);
    }

    public StateResponse state(int id) throws Exception {
        return api.get("/games/" + id + "/state", StateResponse.class);
    }

    public void start(int id) throws Exception {
        api.post("/games/" + id + "/start", null);
    }

    public void vote(int id, int targetId) throws Exception {
        api.post("/games/" + id + "/vote", ApiClient.body("target_id", targetId));
    }

    public JsonNode votes(int id) throws Exception {
        return api.get("/games/" + id + "/votes");
    }

    public JsonNode nightAction(int id, String actionType, Integer targetId) throws Exception {
        var body = ApiClient.body("action_type", actionType);
        if (targetId != null) body.put("target_id", targetId);
        return api.post("/games/" + id + "/action", body);
    }

    public void sendChat(int id, String message, String scope) throws Exception {
        api.post("/games/" + id + "/chat", ApiClient.body(
                "message", message,
                "scope", scope
        ));
    }

    public List<HistoryEntry> history() throws Exception {
        JsonNode r = api.get("/history");
        return M.convertValue(r.path("history"), new TypeReference<List<HistoryEntry>>() {});
    }

    public JsonNode leaderboard() throws Exception {
        return api.get("/leaderboard");
    }

    public JsonNode me() throws Exception {
        return api.get("/me");
    }

    public void updateAvatar(String emoji) throws Exception {
        api.post("/me/avatar", ApiClient.body("avatar_url", emoji));
    }
}
