package com.werewolf;

/**
 * Point d'entrée du JAR exécutable (fat jar).
 * JavaFX refuse qu'une classe étendant Application soit le Main-Class
 * d'un jar lancé sur le classpath — ce launcher contourne la vérification.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
