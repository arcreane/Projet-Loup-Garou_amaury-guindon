package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameSession {
    public int id;
    public String name;
    @JsonProperty("is_ranked")
    public int isRanked;
    public String status;
    public String phase;
    public int round;
    public int timer;
    @JsonProperty("phase_duration")
    public int phaseDuration;
    @JsonProperty("winner_team")
    public String winnerTeam;
    @JsonProperty("pending_hunter_id")
    public Integer pendingHunterId;
    @JsonProperty("nb_players")
    public Integer nbPlayers;

    public boolean ranked() { return isRanked == 1; }

    @Override
    public String toString() {
        String n = (name == null || name.isEmpty()) ? ("Partie #" + id) : name;
        String tag = ranked() ? " 🏆" : "";
        return n + tag + (nbPlayers != null ? "  (" + nbPlayers + " joueurs)" : "");
    }
}
