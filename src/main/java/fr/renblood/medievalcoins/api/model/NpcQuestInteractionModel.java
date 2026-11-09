package fr.renblood.medievalcoins.api.model;

import java.util.List;

public record NpcQuestInteractionModel(
        String questId,
        String name,
        String status,
        String category,
        String type,
        String description,
        String parentId,
        List<String> prerequisitesAll,
        List<String> prerequisitesAny,
        String beginText,
        String endText,
        List<ObjectiveProgress> objectives,
        String missingText,
        List<QuestModel.Reward> itemRewards,
        int money,
        QuestModel.XpReward xp,
        String npcAction,
        boolean canComplete
) {
    public static String resolveNpcText(String text, String npcName) {
        if (text == null) return "";
        String resolvedName = npcName == null || npcName.isBlank() ? "ce PNJ" : npcName;
        return text.replace("{npc}", resolvedName).replace("{NPC}", resolvedName);
    }

    public record ObjectiveProgress(int index, String description, boolean completed) {
    }
}
