package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Invitation {
    public int id;
    @JsonProperty("session_id")
    public int sessionId;
    @JsonProperty("session_name")
    public String sessionName;
    @JsonProperty("is_ranked")
    public int isRanked;
    @JsonProperty("from_id")
    public int fromId;
    @JsonProperty("from_pseudo")
    public String fromPseudo;
    @JsonProperty("from_disc")
    public String fromDisc;
    @JsonProperty("avatar_url")
    public String avatarUrl;

    public boolean ranked() { return isRanked == 1; }

    public String fromTag() {
        return (avatarUrl == null || avatarUrl.isEmpty() ? "👤" : avatarUrl)
                + " " + fromPseudo + "#" + fromDisc;
    }
}
