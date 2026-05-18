package com.werewolf.service;

import com.fasterxml.jackson.databind.JsonNode;

public class AuthService {

    private final ApiClient api = new ApiClient();

    public void login(String pseudo, String password) throws Exception {
        JsonNode r = api.post("/login", ApiClient.body(
                "pseudo",   pseudo,
                "password", password
        ));
        applyAuth(r);
        refreshProfile();
    }

    public void register(String pseudo, String email, String password) throws Exception {
        JsonNode r = api.post("/register", ApiClient.body(
                "pseudo",   pseudo,
                "email",    email,
                "password", password
        ));
        applyAuth(r);
        refreshProfile();
    }

    /** Re-charge l'ELO/avatar/stats/email/discriminator à jour depuis /me. */
    public void refreshProfile() throws Exception {
        JsonNode me = api.get("/me");
        Session.elo           = me.path("elo").asInt(1000);
        Session.gamesPlayed   = me.path("games_played").asInt(0);
        Session.gamesWon      = me.path("games_won").asInt(0);
        Session.email         = me.path("email").asText("");
        Session.discriminator = me.path("discriminator").asText("0000");
        String av = me.path("avatar_url").asText("");
        Session.avatarUrl = av.isEmpty() ? "👤" : av;
    }

    private void applyAuth(JsonNode r) {
        Session.token         = r.path("token").asText();
        Session.playerId      = r.path("player_id").asInt();
        Session.pseudo        = r.path("pseudo").asText();
        Session.discriminator = r.path("discriminator").asText("0000");
    }
}
