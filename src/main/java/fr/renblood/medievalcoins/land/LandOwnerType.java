package fr.renblood.medievalcoins.land;

public enum LandOwnerType {
    PLAYER,
    CITY,
    GUILD,
    FAMILY,
    RELIGIOUS,
    KINGDOM,
    PUBLIC,
    SERVER,
    UNKNOWN;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        for (LandOwnerType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }

        return false;
    }

    public static LandOwnerType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        for (LandOwnerType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        return UNKNOWN;
    }
}
