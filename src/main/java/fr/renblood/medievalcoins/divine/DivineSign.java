package fr.renblood.medievalcoins.divine;

import java.util.UUID;

public enum DivineSign {
    ARDORIUM("Ardorium", "Gardien"),
    SYLVARIA("Sylvaria", "Guerisseur"),
    INQUISORA("Inquisora", "Chercheur"),
    SOLANARE("Solanar\u00e9", "Gardien"),
    AURELIOS("Aurelios", "Guerisseur"),
    EXPLORIEN("Explorien", "Chercheur"),
    IGNOTEMBRIS("Ignotembris", "Gardien"),
    OMBRELUME("Ombrelume", "Guerisseur"),
    SCENARCHE("Sc\u00e9narche", "Chercheur"),
    GLACILUNE("Glacilune", "Gardien"),
    NEVROSANTE("Nevrosante", "Guerisseur"),
    ERUDIHIVER("\u00c9rudihiver", "Chercheur");

    private final String displayName;
    private final String monthRole;

    DivineSign(String displayName, String monthRole) {
        this.displayName = displayName;
        this.monthRole = monthRole;
    }

    public String displayName() {
        return displayName;
    }

    public String monthRole() {
        return monthRole;
    }

    public static DivineSign forPlayer(UUID playerId) {
        int index = Math.floorMod(playerId.hashCode(), values().length);
        return values()[index];
    }
}
