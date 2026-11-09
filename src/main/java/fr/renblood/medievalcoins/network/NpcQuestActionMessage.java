package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.MedievalCoinsAPI;
import fr.renblood.medievalcoins.api.model.NpcQuestInteractionModel;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.events.QuestObjectiveHandler;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record NpcQuestActionMessage(String npcId, String npcName, String texture, String questId, String action) {
    public static void encode(NpcQuestActionMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.npcId); buffer.writeUtf(message.npcName); buffer.writeUtf(message.texture);
        buffer.writeUtf(message.questId); buffer.writeUtf(message.action);
    }

    public static NpcQuestActionMessage decode(FriendlyByteBuf buffer) {
        return new NpcQuestActionMessage(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(NpcQuestActionMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            QuestObjectiveHandler.executeApiTask(() -> {
                try {
                    if ("REFRESH".equalsIgnoreCase(message.action)) {
                        QuestObjectiveHandler.invalidateNpcQuestCaches(player.getUUID());
                        QuestObjectiveHandler.refreshNpcInteractionsAsync(
                                player, message.npcId, message.npcName, message.texture);
                        return;
                    }
                    if ("AUTO_REFRESH".equalsIgnoreCase(message.action)) {
                        MedievalCoinsAPI.openNpcQuestInteractions(player, message.npcId, message.npcName, message.texture);
                        return;
                    }

                    NpcQuestInteractionModel quest = MedievalCoinsAPI.getNpcQuestInteractions(player, message.npcId).stream()
                            .filter(entry -> entry.questId().equalsIgnoreCase(message.questId)).findFirst().orElse(null);
                    if (quest == null) return;
                    if ("START".equals(message.action) && "AVAILABLE".equalsIgnoreCase(quest.status())
                            && "START".equalsIgnoreCase(quest.npcAction())) {
                        PlayerModel profile = ApiClient.getPlayer(player.getUUID().toString());
                        if (profile == null || profile.id == null || profile.id.isBlank()) {
                            throw new IllegalStateException("Profil joueur introuvable pour demarrer la quete");
                        }
                        ApiClient.startQuest(profile.id, quest.questId());
                        QuestObjectiveHandler.invalidatePlayerQuestCache(player.getUUID());
                        MedievalCoinsAPI.showNpcDialogue(player, message.npcName,
                                NpcQuestInteractionModel.resolveNpcText(quest.beginText(), message.npcName), message.texture);
                        QuestObjectiveHandler.syncAvailableNpcQuestsAsync(player);
                    } else if ("COMPLETE".equals(message.action) && "COMPLETE".equalsIgnoreCase(quest.npcAction())) {
                        var result = MedievalCoinsAPI.completeQuestFromNpc(player, quest.questId());
                        QuestObjectiveHandler.invalidatePlayerQuestCache(player.getUUID());
                        MedievalCoinsAPI.showNpcDialogue(player, message.npcName,
                                NpcQuestInteractionModel.resolveNpcText(
                                        result.status().name().equals("COMPLETED") ? quest.endText() : result.message(),
                                        message.npcName),
                                message.texture);
                        if (result.status().name().equals("COMPLETED")) {
                            player.getServer().execute(() -> TutorialManager.recordRomeroQuestCompleted(player));
                        }
                        QuestObjectiveHandler.syncAvailableNpcQuestsAsync(player);
                    } else if ("DIALOGUE".equals(message.action)) {
                        MedievalCoinsAPI.showNpcDialogue(player, message.npcName,
                                NpcQuestInteractionModel.resolveNpcText(
                                        "AVAILABLE".equalsIgnoreCase(quest.status()) ? quest.beginText() : quest.endText(),
                                        message.npcName),
                                message.texture);
                    }
                } catch (Exception e) {
                    MedievalCoin.LOGGER.error("Erreur action quete PNJ", e);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
