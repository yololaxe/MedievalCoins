package fr.renblood.medievalcoins.api;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.NpcModel;
import fr.renblood.medievalcoins.api.model.NpcSpawnModel;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.api.model.NpcQuestInteractionModel;
import fr.renblood.medievalcoins.api.model.QuestCompletionResult;
import fr.renblood.medievalcoins.api.model.XpReferenceData;
import fr.renblood.medievalcoins.api.service.XpReferenceService;
import fr.renblood.medievalcoins.events.QuestObjectiveHandler;
import fr.renblood.medievalcoins.market.counter.ReferenceItemData;
import fr.renblood.medievalcoins.market.service.MarketPriceService;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.MoneyUpdateMessage;
import fr.renblood.medievalcoins.network.OpenNpcDialogueMessage;
import fr.renblood.medievalcoins.network.OpenNpcQuestInteractionsMessage;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.network.PlayerStatsUpdateMessage;
import fr.renblood.medievalcoins.network.RegionHighlightMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;
import java.util.List;

/**
 * API publique pour permettre aux autres mods d'interagir avec MedievalCoins.
 */
public class MedievalCoinsAPI {

    // --- NPC & SPAWNS ---

    public static List<NpcModel> getNpcs() {
        try {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Fetching NPCs list...");
            List<NpcModel> list = ApiClient.getNpcs();
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Fetched " + list.size() + " NPCs.");
            return list;
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to fetch NPCs from API", e);
            return Collections.emptyList();
        }
    }

    public static List<NpcSpawnModel> getNpcSpawns(String worldName) {
        return getNpcSpawns(worldName, true);
    }

    /**
     * Returns NPC spawns for synchronization.
     *
     * @param worldName world/dimension filter, or {@code null} for every world
     * @param includeInactive includes inactive spawns so consumers can remove stale NPCs
     */
    public static List<NpcSpawnModel> getNpcSpawns(String worldName, boolean includeInactive) {
        try {
            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("API: Fetching NPC Spawns for world={}, includeInactive={}", worldName, includeInactive);
            }
            List<NpcSpawnModel> list = ApiClient.getNpcSpawns(worldName, includeInactive);
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Fetched " + list.size() + " spawns.");
            return list;
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to fetch NPC spawns from API", e);
            return Collections.emptyList();
        }
    }

    public static List<NpcSpawnModel> getNpcSpawns() {
        return getNpcSpawns(null, true);
    }

    public static boolean createNpc(NpcModel npc) {
        try {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Creating NPC: " + npc.name + " (" + npc.type + ")");
            boolean result = ApiClient.createNpc(npc);
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Create NPC result: " + result);
            return result;
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to create NPC via API", e);
            return false;
        }
    }

    public static boolean createNpcSpawn(NpcSpawnModel spawn) {
        return createNpcSpawn(null, spawn);
    }

    public static boolean createNpcSpawn(ServerPlayer player, NpcSpawnModel spawn) {
        if (spawn == null || !spawn.hasStableId()) {
            MedievalCoin.LOGGER.error("Failed to create NPC spawn: spawnId is required and must be stable");
            return false;
        }
        try {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Creating NPC Spawn for NPC: " + spawn.npcId + " at " + spawn.x + "," + spawn.y + "," + spawn.z);
            boolean result = player == null
                    ? ApiClient.createNpcSpawn(spawn)
                    : ApiClient.createNpcSpawn(player, spawn);
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Create Spawn result: " + result);
            return result;
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to create NPC spawn via API", e);
            return false;
        }
    }

    public static boolean addQuestToNpc(String npcId, String questId) {
        try {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Adding quest " + questId + " to NPC " + npcId);
            return ApiClient.addQuestToNpc(npcId, questId);
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to add quest to NPC via API", e);
            return false;
        }
    }

    public static boolean removeQuestFromNpc(String npcId, String questId) {
        try {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Removing quest " + questId + " from NPC " + npcId);
            return ApiClient.removeQuestFromNpc(npcId, questId);
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to remove quest from NPC via API", e);
            return false;
        }
    }

    // --- QUÊTES ---
    
    public static List<QuestModel> getAllQuests(String category) {
        try {
            if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info("API: Fetching all quests (category=" + category + ")");
            return ApiClient.getAllQuests(category);
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to fetch quests from API", e);
            return Collections.emptyList();
        }
    }

    public static List<NpcQuestInteractionModel> getNpcQuestInteractions(ServerPlayer player, String npcId) {
        return QuestObjectiveHandler.getNpcInteractions(player, npcId);
    }

    public static QuestCompletionResult completeQuestFromNpc(ServerPlayer player, String questId) {
        return QuestObjectiveHandler.completeFromNpc(player, questId);
    }

    public static void openNpcQuestInteractions(ServerPlayer player, String npcId, String npcName, String texture) {
        QuestObjectiveHandler.openNpcInteractionsAsync(player, npcId, npcName, texture);
    }

    public static void showNpcDialogue(ServerPlayer player, String npcName, String text, String texture) {
        if (player == null) return;
        MedievalCoin.PACKET_HANDLER.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenNpcDialogueMessage(
                        npcName == null ? "" : npcName,
                        text == null ? "" : text,
                        texture == null ? "" : texture
                )
        );
    }

    // --- GESTION XP MÉTIER ---

    public static long getJobXp(ServerPlayer player, String job) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null && pm.experiences != null && pm.experiences.jobs != null) {
            PlayerModel.JobExperience jobExp = pm.experiences.jobs.get(job);
            if (jobExp != null) {
                return jobExp.xp;
            }
        }
        return 0;
    }

    public static int getJobLevel(ServerPlayer player, String job) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null && pm.experiences != null && pm.experiences.jobs != null) {
            PlayerModel.JobExperience jobExp = pm.experiences.jobs.get(job);
            if (jobExp != null) {
                return jobExp.level;
            }
        }
        return 0;
    }

    public static void addJobXp(ServerPlayer player, String job, int amount) {
        modifyJobXp(player, "add", job, amount);
    }

    public static void setJobXp(ServerPlayer player, String job, int amount) {
        modifyJobXp(player, "set", job, amount);
    }

    public static void removeJobXp(ServerPlayer player, String job, int amount) {
        modifyJobXp(player, "remove", job, amount);
    }

    // --- REFERENCES BACKEND ---

    public static List<ReferenceItemData> getPriceReferences() {
        try {
            return MarketPriceService.getReferenceItems();
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to read price references", e);
            return Collections.emptyList();
        }
    }

    public static List<XpReferenceData> getXpReferences() {
        try {
            return XpReferenceService.getReferences();
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Failed to read XP references", e);
            return Collections.emptyList();
        }
    }

    public static long getReferencePrice(String itemId) {
        return getPriceReferences().stream()
                .filter(reference -> matchesItem(reference.itemId(), itemId))
                .map(ReferenceItemData::referencePrice)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(0L);
    }

    public static int getReferenceXp(String action, String job, String itemId) {
        return XpReferenceService.getReferenceXp(action, job, itemId);
    }

    public static int getReferenceXp(String job, String itemId) {
        return getReferenceXp(null, job, itemId);
    }

    // --- GESTION ÉCONOMIE ---

    public static double getBalance(ServerPlayer player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        return pm != null ? pm.money : 0.0;
    }

    public static void addMoney(ServerPlayer player, int amount) {
        modifyMoney(player, amount, true);
    }

    public static void removeMoney(ServerPlayer player, int amount) {
        modifyMoney(player, amount, false);
    }

    // --- RENDU VISUEL ---

    public static void showRegionHighlight(ServerPlayer player, BlockPos min, BlockPos max, int color, int durationTicks) {
        MedievalCoin.PACKET_HANDLER.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RegionHighlightMessage(min, max, color, durationTicks)
        );
    }


    // --- MÉTHODES INTERNES ---

    private static void modifyJobXp(ServerPlayer player, String action, String job, int amount) {
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                
                int finalAmount = amount;
                if ("add".equalsIgnoreCase(action)) {
                    PlayerModel pm = PlayerCache.getPlayer(uuid);
                    if (pm == null) {
                        pm = ApiClient.getPlayer(uuid);
                        if (pm != null) PlayerCache.updatePlayer(pm);
                    }
                    
                    if (pm != null) {
                        double multiplier = pm.skill / 100.0;
                        if (multiplier < 0.0) multiplier = 1.0;
                        finalAmount = (int) (amount * multiplier);
                        
                        if (MedievalCoin.DEBUG_MODE && finalAmount != amount) {
                            MedievalCoin.LOGGER.info("XP Bonus applied for {}: Base={}, Skill={}, Final={}", 
                                    player.getName().getString(), amount, pm.skill, finalAmount);
                        }
                    }
                }

                JsonObject response = ApiClient.manageJobXp(uuid, action, job, finalAmount);

                String jobName = response.get("job").getAsString();
                int newXp = response.get("new_xp").getAsInt();
                int level = response.get("level").getAsInt();

                PlayerModel updatedPlayerModel = ApiClient.getPlayer(uuid);
                int displayAmount = finalAmount;

                player.getServer().execute(() -> {
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStatsUpdateMessage(updatedPlayerModel));

                    String sign = action.equals("remove") ? "-" : (action.equals("add") ? "+" : "=");
                    String color = action.equals("remove") ? "§c" : "§a";
                    String amountStr = action.equals("set") ? String.valueOf(displayAmount) : sign + displayAmount;

                    player.sendSystemMessage(Component.literal(
                            String.format("%s%s XP §6%s §7(Total: §b%d §7- Niveau §e%d§7)", 
                                    color, amountStr, jobName, newXp, level)
                    ), true);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void modifyMoney(ServerPlayer player, int amount, boolean add) {
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            try {
                String uuid = player.getGameProfile().getId().toString();
                PlayerModel pm = PlayerCache.getPlayer(uuid);
                if (pm == null) {
                    pm = ApiClient.getPlayer(uuid);
                    PlayerCache.updatePlayer(pm);
                }

                int newBalance;
                if (add) {
                    newBalance = ApiClient.deposit(pm.id_minecraft, amount);
                } else {
                    newBalance = ApiClient.withdraw(pm.id_minecraft, 0, amount);
                }
                
                pm.money = newBalance;
                PlayerCache.updatePlayer(pm);
                PlayerModel updatedPm = pm;

                player.getServer().execute(() -> {
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new MoneyUpdateMessage(uuid, newBalance));
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStatsUpdateMessage(updatedPm));
                    
                    String action = add ? "reçu" : "payé";
                    String color = add ? "§a" : "§c";
                    player.sendSystemMessage(Component.literal(
                            String.format("%sVous avez %s %d pièces. (Solde: %d)", color, action, amount, newBalance)
                    ), true);
                });

            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Failed to modify money for " + player.getName().getString(), e);
            }
        });
    }

    private static boolean matchesItem(String value, String expected) {
        if (expected == null || expected.isBlank()) return true;
        if (value == null || value.isBlank()) return false;
        return value.equalsIgnoreCase(expected)
                || value.toLowerCase().endsWith(":" + expected.toLowerCase());
    }
}
