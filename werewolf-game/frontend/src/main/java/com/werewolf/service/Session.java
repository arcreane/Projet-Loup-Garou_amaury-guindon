package com.werewolf.service;

/** État global de l'utilisateur connecté (singleton très simple). */
public final class Session {
    public static String token;
    public static int    playerId;
    public static String pseudo;
    public static String avatarUrl = "👤";
    public static int    elo;
    public static int    gamesPlayed;
    public static int    gamesWon;

    public static int    currentGameId;

    private Session() {}

    public static boolean isLoggedIn() { return token != null; }
}
