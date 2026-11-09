package fr.renblood.medievalcoins.divine;

public enum DivineMiniGame {
    RUNE_HUNT(0, "Rune Hunt"),
    DIVINE_SEQUENCE(1, "Divine Sequence"),
    STAR_ALIGNMENT(2, "Star Alignment"),
    SACRED_RHYTHM(3, "Sacred Rhythm");

    private final int id;
    private final String displayName;

    DivineMiniGame(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static DivineMiniGame fromId(int id) {
        for (DivineMiniGame game : values()) {
            if (game.id == id) return game;
        }
        return RUNE_HUNT;
    }
}
