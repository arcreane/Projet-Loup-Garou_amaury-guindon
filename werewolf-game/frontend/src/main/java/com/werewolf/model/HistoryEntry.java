package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryEntry {
    public int id;
    public String name;
    @JsonProperty("is_ranked")
    public int isRanked;
    @JsonProperty("winner_team")
    public String winnerTeam;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("ended_at")
    public String endedAt;
    public String role;
    @JsonProperty("elo_before")
    public Integer eloBefore;
    @JsonProperty("elo_after")
    public Integer eloAfter;

    public boolean ranked() { return isRanked == 1; }

    public Integer eloDelta() {
        if (eloBefore == null || eloAfter == null) return null;
        return eloAfter - eloBefore;
    }

    public boolean iWon() {
        if (winnerTeam == null || role == null) return false;
        boolean isWolf = "WEREWOLF".equals(role);
        return (isWolf && "WEREWOLVES".equals(winnerTeam))
            || (!isWolf && "VILLAGERS".equals(winnerTeam));
    }
}
