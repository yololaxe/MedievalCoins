package fr.renblood.medievalcoins.commands.tree;

/**
 * Enum centralisant les compétences "tree".
 * Chaque entrée correspond à une commande déblocable.
 */
public enum TreeAbility {
    UNBARK("lumberjack", 5),
    FERTILIZE("lumberjack", 9);

    private final String jobId;
    private final int progressionIndex;

    TreeAbility(String jobId, int progressionIndex) {
        this.jobId = jobId;
        this.progressionIndex = progressionIndex;
    }

    public String getJobId() {
        return jobId;
    }

    public int getProgressionIndex() {
        return progressionIndex;
    }
}
