package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.NpcSpawnModel;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.api.model.PlayerQuestStateModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.events.QuestObjectiveHandler;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber
public class QuestCommand {
    private static final String NPC_SPAWN_ID_TAG = "ApiNpcSpawnId";
    private static final double NPC_TARGET_DISTANCE = 8.0D;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("mc").then(Commands.literal("quest")
                .executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    MedievalCoin.PACKET_HANDLER.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new OpenQuestScreenMessage()
                    );
                    c.getSource().sendSuccess(() -> Component.literal("Ouverture du menu des quetes."), false);
                    return 1;
                })));

        d.register(Commands.literal("mc").then(Commands.literal("quest").then(Commands.literal("finish")
                .then(Commands.argument("questId", StringArgumentType.string())
                        .then(Commands.argument("admin", EntityArgument.player())
                                .executes(c -> requestManualValidation(
                                        c.getSource(),
                                        StringArgumentType.getString(c, "questId"),
                                        EntityArgument.getPlayer(c, "admin")
                                )))))));

        d.register(Commands.literal("mc").then(Commands.literal("admin").then(Commands.literal("quest")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("objective")
                        .then(Commands.literal("validate")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("questId", StringArgumentType.string())
                                                .then(Commands.argument("objectiveIndex", IntegerArgumentType.integer(1))
                                                        .executes(c -> validateObjective(
                                                                c.getSource(),
                                                                EntityArgument.getPlayer(c, "target"),
                                                                StringArgumentType.getString(c, "questId"),
                                                                IntegerArgumentType.getInteger(c, "objectiveIndex") - 1
                                                        )))))))
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
                                        )))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("quest", StringArgumentType.greedyString())
                                .executes(c -> assignQuestToLookedNpc(
                                        c.getSource(),
                                        StringArgumentType.getString(c, "quest"),
                                        true
                                ))))
                .then(Commands.literal("unassign")
                        .then(Commands.argument("quest", StringArgumentType.greedyString())
                                .executes(c -> assignQuestToLookedNpc(
                                        c.getSource(),
                                        StringArgumentType.getString(c, "quest"),
                                        false
                                ))))
                .then(Commands.literal("npcinfo")
                        .executes(c -> showLookedNpcInfo(c.getSource()))))));
    }

    private static int assignQuestToLookedNpc(CommandSourceStack src, String questInput, boolean assign) {
        ServerPlayer admin;
        try {
            admin = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Commande reservee aux joueurs admins."));
            return 0;
        }

        Entity npcEntity = findLookedNpc(admin);
        if (npcEntity == null) {
            src.sendFailure(Component.literal("Regardez un PNJ NPC Shopkeeper a moins de 8 blocs."));
            return 0;
        }

        String spawnId = npcEntity.getPersistentData().getString(NPC_SPAWN_ID_TAG);
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            try {
                NpcSpawnModel spawn = findSpawn(spawnId);
                if (spawn == null || spawn.npcId == null || spawn.npcId.isBlank()) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Le spawn PNJ \"" + spawnId + "\" est introuvable dans l'API."))
                    );
                    return;
                }

                QuestModel quest = findQuest(questInput);
                if (quest == null || quest.questId == null || quest.questId.isBlank()) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Quete introuvable ou nom ambigu : " + questInput))
                    );
                    return;
                }

                if (assign) {
                    ApiClient.updateQuestStartNpc(quest.questId, spawn.npcId);
                } else {
                    ApiClient.updateQuestStartNpc(quest.questId, null);
                }

                String action = assign ? " assignee a " : " retiree de ";
                String npcName = firstNonBlank(spawn.npcName, spawn.npcId);
                src.getServer().execute(() ->
                        src.sendSuccess(() -> Component.literal(
                                "Quete \"" + safeQuestName(quest, quest.questId) + "\"" + action
                                        + npcName + " (npcId=" + spawn.npcId + ", spawnId=" + spawn.spawnId + ")."
                        ).withStyle(ChatFormatting.GREEN), true)
                );
            } catch (Exception e) {
                src.getServer().execute(() ->
                        src.sendFailure(Component.literal("Erreur assignation quete/PNJ : " + e.getMessage()))
                );
            }
        });

        return 1;
    }

    private static int showLookedNpcInfo(CommandSourceStack src) {
        ServerPlayer admin;
        try {
            admin = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Commande reservee aux joueurs admins."));
            return 0;
        }

        Entity npcEntity = findLookedNpc(admin);
        if (npcEntity == null) {
            src.sendFailure(Component.literal("Regardez un PNJ NPC Shopkeeper a moins de 8 blocs."));
            return 0;
        }

        String spawnId = npcEntity.getPersistentData().getString(NPC_SPAWN_ID_TAG);
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            try {
                NpcSpawnModel spawn = findSpawn(spawnId);
                src.getServer().execute(() -> {
                    if (spawn == null) {
                        src.sendFailure(Component.literal("Spawn API introuvable : " + spawnId));
                        return;
                    }
                    src.sendSuccess(() -> Component.literal(
                            "PNJ: " + firstNonBlank(spawn.npcName, spawn.npcId)
                                    + " | npcId=" + spawn.npcId
                                    + " | spawnId=" + spawn.spawnId
                    ), false);
                });
            } catch (Exception e) {
                src.getServer().execute(() ->
                        src.sendFailure(Component.literal("Erreur lecture PNJ : " + e.getMessage()))
                );
            }
        });
        return 1;
    }

    private static Entity findLookedNpc(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(NPC_TARGET_DISTANCE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(NPC_TARGET_DISTANCE)).inflate(1.0D),
                entity -> entity.isPickable() && entity.getPersistentData().contains(NPC_SPAWN_ID_TAG),
                NPC_TARGET_DISTANCE * NPC_TARGET_DISTANCE
        );
        return hit == null ? null : hit.getEntity();
    }

    private static NpcSpawnModel findSpawn(String spawnId) throws Exception {
        List<NpcSpawnModel> spawns = ApiClient.getNpcSpawns(null, true);
        if (spawns == null) return null;
        for (NpcSpawnModel spawn : spawns) {
            if (spawn != null && spawnId.equalsIgnoreCase(spawn.spawnId)) return spawn;
        }
        return null;
    }

    private static QuestModel findQuest(String input) throws Exception {
        String query = normalizeLookup(input);
        List<QuestModel> quests = ApiClient.getAllQuests("");
        if (quests == null) return null;

        QuestModel nameMatch = null;
        for (QuestModel quest : quests) {
            if (quest == null) continue;
            if (normalizeLookup(quest.questId).equals(query)) return quest;
            if (normalizeLookup(quest.name).equals(query)) {
                if (nameMatch != null) return null;
                nameMatch = quest;
            }
        }
        return nameMatch;
    }

    private static String normalizeLookup(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "PNJ";
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

        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
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
        });

        return 1;
    }

    private static int validateQuest(CommandSourceStack src, ServerPlayer target, String questId) {
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            try {
                PlayerQuestStateModel state = findActiveQuest(target, questId);
                if (state == null || state.questDetails == null || !isManualApprovalQuest(state.questDetails)) {
                    src.getServer().execute(() ->
                            src.sendFailure(Component.literal("Quete Construction/RP en cours introuvable pour " + target.getName().getString() + "."))
                    );
                    return;
                }

                int validated = validateAllManualObjectives(target, state);
                String questName = safeQuestName(state.questDetails, state.quest_id);

                src.getServer().execute(() -> {
                    src.sendSuccess(() -> Component.literal(validated + " objectif(s) Construction/RP valide(s) pour "
                            + target.getName().getString() + ": " + questName), true);
                    target.sendSystemMessage(Component.literal("Les objectifs Construction/RP de \"" + questName
                            + "\" ont ete valides. Retournez voir le PNJ quand tous les objectifs sont termines.")
                            .withStyle(ChatFormatting.GREEN));
                });
            } catch (Exception e) {
                src.getServer().execute(() ->
                        src.sendFailure(Component.literal("Erreur validation quete: " + e.getMessage()))
                );
            }
        });

        return 1;
    }

    private static int validateObjective(CommandSourceStack src, ServerPlayer target, String questId, int objectiveIndex) {
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            try {
                PlayerQuestStateModel state = findActiveQuest(target, questId);
                if (state == null || state.questDetails == null || state.questDetails.objectives == null
                        || objectiveIndex < 0 || objectiveIndex >= state.questDetails.objectives.size()) {
                    src.getServer().execute(() -> src.sendFailure(Component.literal("Objectif de quete introuvable.")));
                    return;
                }

                QuestModel.Objective objective = state.questDetails.objectives.get(objectiveIndex);
                if (!isManualObjective(objective)) {
                    src.getServer().execute(() -> src.sendFailure(Component.literal(
                            "L'objectif " + (objectiveIndex + 1) + " n'est pas un objectif Construction/RP.")));
                    return;
                }

                QuestObjectiveHandler.completeManualObjective(target, state.quest_id, objectiveIndex);
                String description = objective.description == null || objective.description.isBlank()
                        ? objective.type : objective.description;
                src.getServer().execute(() -> {
                    src.sendSuccess(() -> Component.literal("Objectif " + (objectiveIndex + 1) + " valide pour "
                            + target.getName().getString() + ": " + description), true);
                    target.sendSystemMessage(Component.literal("Objectif valide par un admin : " + description)
                            .withStyle(ChatFormatting.GREEN));
                });
            } catch (Exception e) {
                src.getServer().execute(() -> src.sendFailure(Component.literal("Erreur validation objectif: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static int validateAllManualObjectives(ServerPlayer target, PlayerQuestStateModel state) {
        int validated = 0;
        for (int index = 0; index < state.questDetails.objectives.size(); index++) {
            if (isManualObjective(state.questDetails.objectives.get(index))) {
                QuestObjectiveHandler.completeManualObjective(target, state.quest_id, index);
                validated++;
            }
        }
        return validated;
    }

    private static int refuseQuest(CommandSourceStack src, ServerPlayer target, String questId) {
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
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
        });

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
            if (isManualObjective(objective)) return true;
        }
        return false;
    }

    private static boolean isManualObjective(QuestModel.Objective objective) {
        if (objective == null) return false;
        String type = normalize(objective.type);
        return type.contains("construct") || type.contains("rp") || type.contains("roleplay");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replace(" ", "").replace("_", "").replace("-", "");
    }

    private static Component buildAdminRequestMessage(ServerPlayer requester, QuestModel quest) {
        String playerName = requester.getName().getString();
        String questName = safeQuestName(quest, quest.questId);
        String questId = quest.questId != null && !quest.questId.isEmpty() ? quest.questId : questName;

        Component message = Component.literal(playerName + " demande une validation pour \"" + questName + "\" ")
                .withStyle(ChatFormatting.GOLD)
                .append(commandButton("[TP]", "/tp " + playerName, ChatFormatting.AQUA, "Se teleporter sur " + playerName));
        for (int index = 0; index < quest.objectives.size(); index++) {
            if (!isManualObjective(quest.objectives.get(index))) continue;
            int displayIndex = index + 1;
            String objectiveType = manualObjectiveType(quest.objectives.get(index));
            message = message.copy().append(Component.literal(" ")).append(commandButton(
                    "[Valider objectif " + displayIndex + " (" + objectiveType + ")]",
                    "/mc admin quest objective validate " + playerName + " " + quoteCommandArg(questId) + " " + displayIndex,
                    ChatFormatting.GREEN,
                    "Valider uniquement l'objectif " + displayIndex + " (" + objectiveType + ")"));
        }
        return message.copy().append(Component.literal(" ")).append(commandButton(
                "[Non valide]", "/mc admin quest refuse " + playerName + " " + quoteCommandArg(questId),
                ChatFormatting.RED, "Laisser la quete en cours"));
    }

    private static String manualObjectiveType(QuestModel.Objective objective) {
        String type = normalize(objective == null ? null : objective.type);
        if (type.contains("construct")) return "Construction";
        if (type.contains("roleplay") || type.contains("rp")) return "RP";
        return objective == null || objective.type == null || objective.type.isBlank() ? "Manuel" : objective.type;
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
