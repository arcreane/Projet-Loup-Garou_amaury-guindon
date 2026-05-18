package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    public int id;
    public String pseudo;
    @JsonProperty("avatar_url")
    public String avatarUrl;
    public String message;
    public String scope;        // ALL ou WEREWOLVES
    @JsonProperty("created_at")
    public String createdAt;
}
