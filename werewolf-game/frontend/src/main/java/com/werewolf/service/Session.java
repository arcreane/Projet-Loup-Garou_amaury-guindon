package com.werewolf.service;

public final class Session {
    public static String token;
    public static int    playerId;
    public static String pseudo;
    public static String discriminator = "0000";
    public static String email;
    public static String avatarUrl = "👤";
    public static int    elo;
    public static int    gamesPlayed;
    public static int    gamesWon;

    public static int    currentGameId;

    private Session() {}

    public static boolean isLoggedIn() { return token != null; }

    /** "pseudo#0042" */
    public static String fullTag() { return pseudo + "#" + discriminator; }
}
