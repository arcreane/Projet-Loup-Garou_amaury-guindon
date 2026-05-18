package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Friend {
    public int id;
    public String pseudo;
    public String discriminator;
    @JsonProperty("avatar_url")
    public String avatarUrl;
    public Integer elo;
    @JsonProperty("games_played")
    public Integer gamesPlayed;
    @JsonProperty("games_won")
    public Integer gamesWon;
    /** ONLINE / OFFLINE / IN_GAME */
    public String status;
    @JsonProperty("session_id")
    public Integer sessionId;

    public String fullTag() {
        return (avatarUrl == null || avatarUrl.isEmpty() ? "👤" : avatarUrl)
                + " " + pseudo + "#" + discriminator;
    }

    public String statusIcon() {
        if (status == null) return "⚫";
        return switch (status) {
            case "ONLINE"  -> "🟢";
            case "IN_GAME" -> "🎮";
            default        -> "⚫";
        };
    }
}
