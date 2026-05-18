package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Réponse de GET /api/games/{id}/state. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateResponse {
    public GameSession session;
    @JsonProperty("my_role")
    public String myRole;
    @JsonProperty("i_am_dead")
    public boolean iAmDead;
    public List<Player> players;
    public List<GameLog> logs;
    public List<ChatMessage> chat;

    public Role myRoleEnum() { return Role.parse(myRole); }
}
