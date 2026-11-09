package fr.renblood.medievalcoins.api.model;

import java.util.List;

public record QuestCompletionResult(Status status, String questId, String message, List<String> missingObjectives) {
    public enum Status {
        COMPLETED,
        MISSING_OBJECTIVES,
        ALREADY_COMPLETED,
        ERROR
    }
}
