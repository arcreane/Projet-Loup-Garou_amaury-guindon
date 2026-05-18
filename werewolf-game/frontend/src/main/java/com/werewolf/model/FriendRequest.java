package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FriendRequest {
    public int id;
    public String pseudo;
    public String discriminator;
    @JsonProperty("avatar_url")
    public String avatarUrl;

    public String fullTag() {
        return (avatarUrl == null || avatarUrl.isEmpty() ? "👤" : avatarUrl)
                + " " + pseudo + "#" + discriminator;
    }
}
