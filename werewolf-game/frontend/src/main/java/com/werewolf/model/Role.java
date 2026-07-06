package com.werewolf.model;

public enum Role {
    VILLAGER("Villageois"),
    WEREWOLF("Loup-Garou"),
    SEER("Voyante"),
    WITCH("Sorcière"),
    HUNTER("Chasseur"),
    LITTLE_GIRL("Petite Fille");

    private final String label;
    Role(String l) { this.label = l; }
    public String label() { return label; }

    public static Role parse(String s) {
        if (s == null) return null;
        try { return Role.valueOf(s); } catch (Exception e) { return null; }
    }
}
