package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {
    public int id;
    public String pseudo;
    public String discriminator;
    @JsonProperty("avatar_url")
    public String avatarUrl;
    public Integer elo;
    @JsonProperty("is_alive")
    public int isAlive;
    @JsonProperty("is_host")
    public int isHost;
    public String role;
    @JsonProperty("elo_before")
    public Integer eloBefore;
    @JsonProperty("elo_after")
    public Integer eloAfter;

    public boolean alive() { return isAlive == 1; }
    public boolean host()  { return isHost  == 1; }
    public Role roleEnum() { return Role.parse(role); }
    public String avatar() { return avatarUrl == null || avatarUrl.isEmpty() ? "👤" : avatarUrl; }

    public String fullTag() {
        return pseudo + (discriminator != null ? "#" + discriminator : "");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(avatar()).append(' ').append(fullTag());
        if (elo != null) sb.append("  (").append(elo).append(')');
        if (!alive()) sb.append(" ☠");
        if (host())   sb.append(" 👑");
        return sb.toString();
    }
}
