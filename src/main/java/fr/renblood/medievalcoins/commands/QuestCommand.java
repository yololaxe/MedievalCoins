package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.api.model.PlayerQuestStateModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.network.OpenQuestScreenMessage;
import fr.renblood.medievalcoins.network.ApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

@Mod.EventBusSubscriber
public class QuestCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("quest")
                .executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    MedievalCoin.PACKET_HANDLER.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new OpenQuestScreenMessage()
                    );
                    c.getSource().sendSuccess(() -> Component.literal("Ouverture du menu des quetes."), false);
                    return 1;
                }));

        d.register(Commands.literal("finish")
                .then(Commands.argument("questId", StringArgumentType.string())
                        .then(Commands.argument("admin", EntityArgument.player())
                                .executes(c -> requestManualValidation(
                                        c.getSource(),
                                        StringArgumentType.getString(c, "questId"),
                                        EntityArgument.getPlayer(c, "admin")
                                )))));

        d.register(Commands.literal("questadmin")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("questId", StringArgumentType.string())
                                        .executes(c -> validateQuest(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "target"),
                                                StringArgumentType.getString(c, "questId")
                                        )))))
                .then(Commands.literal("refuse")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("questId", StringArgumentType.string())
                                        .executes(c -> refuseQuest(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "target"),
                                                StringArgumentType.getString(c, "questId")
                                        ))))));
    }

    private static int requestManualValidation(CommandSourceStack src, String questId, ServerPlayer admin) {
        ServerPlayer requester;
        try {
            requester = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Commande reservee aux joueurs."));
            return 0;
        }

        if (!admin.createCommandSourceStack().hasPermission(2)) {
            src.sendFailure(Component.literal(admin.getName().getString() + " n'est pas admin."));
            return 0;
        }

        new Thread(() -> {
            try {
                PlayerQuestStateModel state = findActiveQuest(requester, questId);
                if (state == null || state.questDetails == null) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Aucune quete en cours trouvee avec l'ID: " + questId))
                    );
                    return;
                }

                QuestModel quest = state.questDetails;
                if (!isManualApprovalQuest(quest)) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Cette quete n'a aucun objectif Construction/RP."))
                    );
                    return;
                }

                src.getServer().execute(() -> {
                    admin.sendSystemMessage(buildAdminRequestMessage(requester, quest));
                    src.sendSuccess(() -> Component.literal("Demande envoyee a " + admin.getName().getString() + "."), false);
                });
            } catch (Exception e) {
                src.getServer().execute(() ->
                        src.sendFailure(Component.literal("Erreur API quetes: " + e.getMessage()))
                );
            }
        }, "MedievalCoins-QuestFinishRequest").start();

        return 1;
    }

    private static int validateQuest(CommandSourceStack src, ServerPlayer target, String questId) {
        new Thread(() -> {
            try {
                PlayerQuestStateModel state = findActiveQuest(target, questId);
                if (state == null || state.questDetails == null || !isManualApprovalQuest(state.questDetails)) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Quete Construction/RP en cours introuvable pour " + target.getName().getString() + "."))
                    );
                    return;
                }

                String playerId = resolveBackendPlayerId(target, state);
                ApiClient.completeQuest(playerId, state.quest_id);
                String questName = safeQuestName(state.questDetails, state.quest_id);

                src.getServer().execute(() -> {
                    src.sendSuccess(() -> Component.literal("Quete validee pour " + target.getName().getString() + ": " + questName), true);
                    target.sendSystemMessage(Component.literal("Votre quete \"" + questName + "\" a ete validee par un admin.").withStyle(ChatFormatting.GREEN));
                });
            } catch (Exception e) {
                src.getServer().execute(() ->
                        src.sendFailure(Component.literal("Erreur validation quete: " + e.getMessage()))
                );
            }
        }, "MedievalCoins-QuestManualValidate").start();

        return 1;
    }

    private static int refuseQuest(CommandSourceStack src, ServerPlayer target, String questId) {
        new Thread(() -> {
            try {
                PlayerQuestStateModel state = findActiveQuest(target, questId);
                if (state == null || state.questDetails == null || !isManualApprovalQuest(state.questDetails)) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Quete Construction/RP en cours introuvable pour " + target.getName().getString() + "."))
                    );
                    return;
                }

                String questName = safeQuestName(state.questDetails, state.quest_id);
                src.getServer().execute(() -> {
                    src.sendSuccess(() -> Component.literal("Quete non validee, elle continue pour " + target.getName().getString() + ": " + questName), false);
                    target.sendSystemMessage(Component.literal("Votre quete \"" + questName + "\" n'a pas ete validee. Elle continue.").withStyle(ChatFormatting.YELLOW));
                });
            } catch (Exception e) {
                src.getServer().execute(() ->
                        src.sendFailure(Component.literal("Erreur verification quete: " + e.getMessage()))
                );
            }
        }, "MedievalCoins-QuestManualRefuse").start();

        return 1;
    }

    private static PlayerQuestStateModel findActiveQuest(ServerPlayer player, String questId) throws Exception {
        String uuid = player.getGameProfile().getId().toString();
        List<PlayerQuestStateModel> activeQuests = ApiClient.getActiveQuests(uuid, "");
        if (activeQuests == null) return null;

        for (PlayerQuestStateModel state : activeQuests) {
            if (state == null || state.quest_id == null || state.quest_id.isEmpty()) continue;
            if (state.questDetails == null) {
                try {
                    state.questDetails = ApiClient.getQuestDetails(state.quest_id);
                } catch (Exception ignored) {
                }
            }

            if (matchesQuestId(questId, state.quest_id)) {
                return state;
            }
        }

        return null;
    }

    private static String resolveBackendPlayerId(ServerPlayer player, PlayerQuestStateModel state) throws Exception {
        if (state.player_id != null && !state.player_id.isEmpty()) {
            return state.player_id;
        }

        String minecraftId = player.getGameProfile().getId().toString();
        PlayerModel profile = ApiClient.getPlayer(minecraftId);
        if (profile != null) {
            if (profile.id != null && !profile.id.isEmpty()) {
                return profile.id;
            }
            if (profile.id_minecraft != null && !profile.id_minecraft.isEmpty()) {
                return profile.id_minecraft;
            }
        }

        return minecraftId;
    }

    private static boolean matchesQuestId(String input, String questId) {
        if (input == null) return false;
        return input.equalsIgnoreCase(questId);
    }

    private static boolean isManualApprovalQuest(QuestModel quest) {
        if (quest == null || quest.objectives == null) return false;
        for (QuestModel.Objective objective : quest.objectives) {
            if (objective == null) continue;
            String type = normalize(objective.type);
            if (type.contains("construct") || type.contains("rp")) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replace(" ", "").replace("_", "").replace("-", "");
    }

    private static Component buildAdminRequestMessage(ServerPlayer requester, QuestModel quest) {
        String playerName = requester.getName().getString();
        String questName = safeQuestName(quest, quest.questId);
        String questId = quest.questId != null && !quest.questId.isEmpty() ? quest.questId : questName;

        return Component.literal(playerName + " a finis la quete \"" + questName + "\" ")
                .withStyle(ChatFormatting.GOLD)
                .append(commandButton("[TP]", "/tp " + playerName, ChatFormatting.AQUA, "Se teleporter sur " + playerName))
                .append(Component.literal(" "))
                .append(commandButton("[Valider]", "/questadmin validate " + playerName + " " + quoteCommandArg(questId), ChatFormatting.GREEN, "Valider la quete"))
                .append(Component.literal(" "))
                .append(commandButton("[Non valide]", "/questadmin refuse " + playerName + " " + quoteCommandArg(questId), ChatFormatting.RED, "Laisser la quete en cours"));
    }

    private static Component commandButton(String label, String command, ChatFormatting color, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static String safeQuestName(QuestModel quest, String fallback) {
        if (quest != null && quest.name != null && !quest.name.isEmpty()) return quest.name;
        return fallback == null || fallback.isEmpty() ? "quete" : fallback;
    }

    private static String quoteCommandArg(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
