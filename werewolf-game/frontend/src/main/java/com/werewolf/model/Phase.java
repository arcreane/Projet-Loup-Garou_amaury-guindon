package com.werewolf.model;

/** Sous-phases utilisées par le backend (chaîne libre). */
public final class Phase {
    public static final String NIGHT_WEREWOLF = "NIGHT_WEREWOLF";
    public static final String NIGHT_SEER     = "NIGHT_SEER";
    public static final String NIGHT_WITCH    = "NIGHT_WITCH";
    public static final String DAY_VOTE       = "DAY_VOTE";
    public static final String ENDED          = "ENDED";

    private Phase() {}

    public static String prettyName(String phase) {
        if (phase == null) return "";
        return switch (phase) {
            case NIGHT_WEREWOLF -> "Nuit - Loups-Garous";
            case NIGHT_SEER     -> "Nuit - Voyante";
            case NIGHT_WITCH    -> "Nuit - Sorcière";
            case DAY_VOTE       -> "Jour - Vote du village";
            case ENDED          -> "Partie terminée";
            default             -> phase;
        };
    }
}
