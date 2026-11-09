package fr.renblood.medievalcoins.divine;

public enum DivineEffect {
    FORCE_BONUS("Force", 2, 3, false),
    FORCE_MALUS("Force", -2, -1, false),
    LIFE_BONUS("Vie", 4, 6, false),
    LIFE_MALUS("Vie", -4, -2, false),
    SPEED_BONUS("Vitesse", 10, 15, true),
    SPEED_MALUS("Vitesse", -10, -5, true),
    RESISTANCE_BONUS("Resistance", 2, 3, false),
    REACH_MALUS("Reach", -1, 0, false),
    SKILL_BONUS("Competence", 1, 2, false),
    SKILL_MALUS("Competence", -2, -1, false),
    REGENERATION_BONUS("Regeneration", 1, 2, false),
    REGENERATION_MALUS("Regeneration", -1, 0, false);

    private final String statName;
    private final int normalValue;
    private final int prayedValue;
    private final boolean percentage;

    DivineEffect(String statName, int normalValue, int prayedValue, boolean percentage) {
        this.statName = statName;
        this.normalValue = normalValue;
        this.prayedValue = prayedValue;
        this.percentage = percentage;
    }

    public int value(boolean prayed) {
        return prayed ? prayedValue : normalValue;
    }

    public String describe(boolean prayed) {
        int value = value(prayed);
        return statName + " " + (value >= 0 ? "+" : "") + value + (percentage ? "%" : "");
    }
}
