package fr.renblood.medievalcoins.tutorial;

/** Shared immutable identity rules; no HTTP needed to offer Nagaki's tutorial. */
public final class TutorialRules {
    public static final String NAGAKI_ID = "npc_1781688807398";
    private TutorialRules() {}
    public static boolean isNagaki(String id, String name) {
        return NAGAKI_ID.equals(id) || name != null && name.strip().equalsIgnoreCase("Nagaki");
    }
}
