package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameLog {
    public String message;
    public String visibility;
    @JsonProperty("created_at")
    public String createdAt;
}
