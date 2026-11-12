package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.NpcModel;
import fr.renblood.medievalcoins.api.model.NpcSpawnModel;
import fr.renblood.medievalcoins.api.model.NpcQuestInteractionModel;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.api.model.PlayerQuestStateModel;
import fr.renblood.medievalcoins.api.model.QuestCompletionResult;
import fr.renblood.medievalcoins.api.model.QuestModel;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.NpcQuestAvailabilityMessage;
import fr.renblood.medievalcoins.network.OpenNpcQuestInteractionsMessage;
import fr.renblood.medievalcoins.network.QuestProgressMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class QuestObjectiveHandler {
    private static final String PROGRESS_TAG = "MedievalCoinsQuestObjectives";
    private static final String NPC_SPAWN_ID_TAG = "ApiNpcSpawnId";
    private static final long CATALOG_CACHE_MS = 300_000L;
    private static final long QUEST_DETAILS_CACHE_MS = 60_000L;
    private static final long PLAYER_QUEST_CACHE_MS = 15_000L;
    private static final long NPC_INTERACTION_LOCK_MS = 2_000L;
    private static final Pattern COORDINATE_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Set<UUID> EVALUATIONS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> NPC_INTERACTIONS_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Set<UUID> AVAILABILITY_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> PENDING_NPC_INTERACTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<PlayerQuestStateModel>> ACTIVE_QUEST_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CachedQuest> QUEST_DETAILS_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedPlayerQuests> PLAYER_QUEST_CACHE = new ConcurrentHashMap<>();
    private static final java.util.concurrent.Executor API_EXECUTOR =
            fr.renblood.medievalcoins.network.ApiExecutor.executor();
    private static volatile CatalogCache catalogCache;

    private QuestObjectiveHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        if (player.tickCount % 10 == 0) {
            List<PlayerQuestStateModel> cachedQuests = ACTIVE_QUEST_CACHE.get(player.getUUID());
            if (cachedQuests != null) {
                evaluateOnServer(player, cachedQuests, null);
            }
        }
        if (player.tickCount == 1 || player.tickCount % 1200 == 0) {
            evaluateAsync(player);
            syncAvailableNpcQuestsAsync(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        String spawnId = target.getPersistentData().getString(NPC_SPAWN_ID_TAG);
        if (spawnId.isBlank()) return;

        PENDING_NPC_INTERACTIONS.put(player.getUUID(), spawnId);
        evaluateAsync(player);
    }

    private static void evaluateAsync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        if (!EVALUATIONS_IN_FLIGHT.add(playerUuid)) return;

        String interactedSpawnId = PENDING_NPC_INTERACTIONS.remove(playerUuid);
        API_EXECUTOR.execute(() -> {
            try {
                List<PlayerQuestStateModel> activeQuests = loadActiveQuests(playerUuid.toString());
                NpcIdentity interactedNpc = resolveNpc(interactedSpawnId);
                ACTIVE_QUEST_CACHE.put(playerUuid, activeQuests);
                player.getServer().execute(() -> {
                    clearInactiveQuestProgress(player, activeQuests);
                    evaluateOnServer(player, activeQuests, interactedNpc);
                    syncProgress(player, activeQuests);
                });
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Impossible de verifier les objectifs de quete de {}", player.getName().getString(), e);
            } finally {
                EVALUATIONS_IN_FLIGHT.remove(playerUuid);
            }
        });
    }

    public static void openNpcInteractionsAsync(ServerPlayer player, String npcId, String npcName, String texture) {
        openNpcInteractionsAsync(player, npcId, npcName, texture, false);
    }

    public static void refreshNpcInteractionsAsync(ServerPlayer player, String npcId, String npcName, String texture) {
        openNpcInteractionsAsync(player, npcId, npcName, texture, true);
    }

    private static void openNpcInteractionsAsync(
            ServerPlayer player, String npcId, String npcName, String texture, boolean notifyPlayer) {
        UUID playerUuid = player.getUUID();
        long requestStartedAt = System.currentTimeMillis();
        Long previousRequest = NPC_INTERACTIONS_IN_FLIGHT.putIfAbsent(playerUuid, requestStartedAt);
        if (previousRequest != null && requestStartedAt - previousRequest < NPC_INTERACTION_LOCK_MS) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("Une actualisation des quetes est deja en cours.")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        if (previousRequest != null) {
            NPC_INTERACTIONS_IN_FLIGHT.replace(playerUuid, previousRequest, requestStartedAt);
        }

        API_EXECUTOR.execute(() -> {
            try {
                List<NpcQuestInteractionModel> quests = getNpcInteractions(player, npcId);
                player.getServer().execute(() -> {
                    if (player.isRemoved() || player.hasDisconnected()) return;
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                            new OpenNpcQuestInteractionsMessage(npcId, npcName, texture, quests));
                    if (notifyPlayer) {
                        player.sendSystemMessage(Component.literal("Quetes du PNJ actualisees.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                });
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Impossible d'ouvrir les interactions du PNJ {}", npcId, e);
                if (notifyPlayer) {
                    player.getServer().execute(() -> player.sendSystemMessage(
                            Component.literal("API indisponible : dernier cache de quetes conserve.")
                                    .withStyle(ChatFormatting.RED)));
                }
            } finally {
                NPC_INTERACTIONS_IN_FLIGHT.remove(playerUuid, requestStartedAt);
            }
        });
    }

    public static void executeApiTask(Runnable task) {
        API_EXECUTOR.execute(task);
    }

    public static void invalidatePlayerQuestCache(UUID playerUuid) {
        CachedPlayerQuests cached = PLAYER_QUEST_CACHE.get(playerUuid);
        if (cached != null) PLAYER_QUEST_CACHE.put(playerUuid, new CachedPlayerQuests(cached.states(), 0L));
    }

    public static void invalidateNpcQuestCaches(UUID playerUuid) {
        invalidatePlayerQuestCache(playerUuid);
        CatalogCache cached = catalogCache;
        if (cached != null) catalogCache = new CatalogCache(cached.spawns(), cached.npcs(), cached.quests(), 0L);
    }

    public static void completeManualObjective(ServerPlayer player, String questId, int objectiveIndex) {
        if (!player.getServer().isSameThread()) {
            player.getServer().execute(() -> completeManualObjective(player, questId, objectiveIndex));
            return;
        }
        CompoundTag progress = getQuestProgress(player, questId);
        progress.putBoolean(Integer.toString(objectiveIndex), true);
        saveQuestProgress(player, questId, progress);
        syncProgress(player, ACTIVE_QUEST_CACHE.getOrDefault(player.getUUID(), List.of()));
    }

    public static void syncAvailableNpcQuestsAsync(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        if (!AVAILABILITY_IN_FLIGHT.add(playerUuid)) return;

        API_EXECUTOR.execute(() -> {
            try {
                Set<String> availableSpawnIds = getAvailableNpcSpawnIds(player);
                player.getServer().execute(() -> {
                    if (player.isRemoved() || player.hasDisconnected()) return;
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                            new NpcQuestAvailabilityMessage(availableSpawnIds));
                });
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Impossible de synchroniser les nouvelles quêtes PNJ pour {}",
                        player.getName().getString(), e);
            } finally {
                AVAILABILITY_IN_FLIGHT.remove(playerUuid);
            }
        });
    }

    private static List<PlayerQuestStateModel> loadActiveQuests(String minecraftId) throws Exception {
        List<PlayerQuestStateModel> activeQuests;
        try {
            activeQuests = ApiClient.getActiveQuests(minecraftId, "");
        } catch (Exception e) {
            List<PlayerQuestStateModel> cached = ACTIVE_QUEST_CACHE.get(UUID.fromString(minecraftId));
            if (cached != null) return cached;
            throw e;
        }
        if (activeQuests == null) return List.of();

        for (PlayerQuestStateModel state : activeQuests) {
            if (state == null || state.quest_id == null || state.quest_id.isBlank()) continue;
            if (state.questDetails == null) {
                state.questDetails = getQuestDetailsCached(state.quest_id);
            }
        }
        return activeQuests;
    }

    private static NpcIdentity resolveNpc(String spawnId) throws Exception {
        if (spawnId == null || spawnId.isBlank()) return null;

        List<NpcSpawnModel> spawns = getCatalogCached().spawns();
        if (spawns != null) {
            for (NpcSpawnModel spawn : spawns) {
                if (spawn != null && equalsIgnoreCase(spawnId, spawn.spawnId)) {
                    return new NpcIdentity(spawn.spawnId, spawn.npcId, spawn.npcName);
                }
            }
        }
        return new NpcIdentity(spawnId, null, null);
    }

    private static void evaluateOnServer(ServerPlayer player, List<PlayerQuestStateModel> activeQuests, NpcIdentity interactedNpc) {
        if (player.isRemoved() || player.hasDisconnected()) return;

        for (PlayerQuestStateModel state : activeQuests) {
            QuestModel quest = state == null ? null : state.questDetails;
            if (quest == null || quest.objectives == null || quest.objectives.isEmpty()) continue;

            String questId = firstNonBlank(state.quest_id, quest.questId);
            if (questId == null) continue;

            CompoundTag progress = getQuestProgress(player, questId);
            boolean changed = false;

            for (int index = 0; index < quest.objectives.size(); index++) {
                QuestModel.Objective objective = quest.objectives.get(index);
                if (objective == null) continue;

                String objectiveKey = Integer.toString(index);
                if (progress.getBoolean(objectiveKey)) continue;

                String type = normalize(objective.type);
                if ("location".equals(type)) {
                    if (isAtLocation(player.blockPosition(), objective.coord)) {
                        progress.putBoolean(objectiveKey, true);
                        changed = true;
                        notifyObjective(player, objective);
                    }
                } else if ("talk".equals(type) || "npc".equals(type)) {
                    if (interactedNpc != null && interactedNpc.matches(objective.getTargetNpcId())) {
                        progress.putBoolean(objectiveKey, true);
                        changed = true;
                        notifyObjective(player, objective);
                    }
                } else if ("item".equals(type) || "items".equals(type)) {
                    if (interactedNpc != null
                            && (interactedNpc.matches(objective.getTargetNpcId())
                            || interactedNpc.matches(quest.getCompletionNpcId()))
                            && hasRequiredItems(player, itemRequirements(objective))) {
                        removeRequiredItems(player, itemRequirements(objective));
                        progress.putBoolean(objectiveKey, true);
                        changed = true;
                        notifyObjective(player, objective);
                    }
                } else {
                    // Les objectifs manuels restent en attente d'une validation externe.
                }
            }

            if (changed) {
                saveQuestProgress(player, questId, progress);
            }

            // La quete est terminee uniquement lorsque NPC Shopkeeper affiche le dialogue final.
        }
    }

    public static QuestCompletionResult completeFromNpc(ServerPlayer player, String questId) {
        if(player.getServer().isSameThread())return new QuestCompletionResult(QuestCompletionResult.Status.ERROR,questId,"Validation à relancer depuis le dialogue PNJ.",List.of());
        try {
            List<PlayerQuestStateModel> states = getPlayerQuestsCached(player);
            if (states != null && states.stream().anyMatch(state -> state != null
                    && equalsIgnoreCase(questId, state.quest_id)
                    && "COMPLETED".equalsIgnoreCase(state.status))) {
                return new QuestCompletionResult(QuestCompletionResult.Status.ALREADY_COMPLETED, questId,
                        "Cette quete est deja terminee.", List.of());
            }
        } catch (Exception e) {
            MedievalCoin.LOGGER.warn("Impossible de verifier si la quete {} est deja terminee", questId, e);
        }

        List<PlayerQuestStateModel> activeQuests = ACTIVE_QUEST_CACHE.get(player.getUUID());
        if (activeQuests == null) {
            return new QuestCompletionResult(QuestCompletionResult.Status.ERROR, questId,
                    "Progression de quete non chargee.", List.of());
        }

        for (PlayerQuestStateModel state : activeQuests) {
            if (state == null || !equalsIgnoreCase(questId, state.quest_id) || state.questDetails == null) continue;
            QuestModel quest = state.questDetails;
            CompletableFuture<List<String>> validation=new CompletableFuture<>();
            player.getServer().execute(()->{
                if(!fr.renblood.medievalcoins.bank.BankService.acquire(player)){validation.completeExceptionally(new IllegalStateException("Une opération économique est déjà en cours."));return;}
                try{
                    if(player.hasDisconnected())throw new IllegalStateException("Joueur déconnecté");
                    evaluateQuestObjectivesForNpc(player,state,quest,null);
                    List<String> pending=getMissingObjectives(player,questId,quest);
                    fr.renblood.medievalcoins.bank.BankPlayerSnapshot.save(player);
                    validation.complete(pending);
                }catch(Exception failure){validation.completeExceptionally(failure);}
                finally{fr.renblood.medievalcoins.bank.BankService.release(player);}
            });
            List<String> missing;
            try{missing=validation.get(10,java.util.concurrent.TimeUnit.SECONDS);}
            catch(Exception failure){return new QuestCompletionResult(QuestCompletionResult.Status.ERROR,questId,"Validation différée. Vos objets et votre progression restent conservés.",List.of());}
            if (!missing.isEmpty()) {
                return new QuestCompletionResult(QuestCompletionResult.Status.MISSING_OBJECTIVES, questId,
                        "Objectifs manquants : " + String.join(", ", missing), missing);
            }

            try {
                fr.renblood.medievalcoins.bank.QuestRewardService.complete(player,questId);
                grantRewards(player, quest);
                player.getServer().execute(()->{
                    ACTIVE_QUEST_CACHE.computeIfPresent(player.getUUID(),(id,list)->list.stream().filter(value->value!=state).toList());
                    syncProgress(player,ACTIVE_QUEST_CACHE.getOrDefault(player.getUUID(),List.of()));
                });
                player.getServer().execute(() -> player.sendSystemMessage(
                        Component.literal("Quete terminee : " + firstNonBlank(quest.name, questId))
                                .withStyle(ChatFormatting.GREEN)));
                return new QuestCompletionResult(QuestCompletionResult.Status.COMPLETED, questId,
                        "Quête validée. Récompenses enregistrées ; versement bancaire à confirmer.", List.of());
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Impossible de terminer la quete {} depuis le dialogue PNJ", questId, e);
                return new QuestCompletionResult(QuestCompletionResult.Status.ERROR, questId, e.getMessage(), List.of());
            }
        }
        return new QuestCompletionResult(QuestCompletionResult.Status.ERROR, questId,
                "Quete active introuvable.", List.of());
    }

    public static List<NpcQuestInteractionModel> getNpcInteractions(ServerPlayer player, String npcId) {
        try {
            String resolvedNpcId = npcId;
            CatalogCache catalog = getCatalogCached();
            List<NpcSpawnModel> spawns = catalog.spawns();
            if (spawns != null) {
                for (NpcSpawnModel spawn : spawns) {
                    if (spawn == null || (!equalsIgnoreCase(npcId, spawn.spawnId)
                            && !equalsIgnoreCase(npcId, spawn.npcId))) continue;
                    resolvedNpcId = firstNonBlank(spawn.npcId, resolvedNpcId);
                }
            }

            String finalNpcId = resolvedNpcId;
            List<QuestModel> allQuests = catalog.quests();
            List<PlayerQuestStateModel> states = getPlayerQuestsCached(player);
            Map<String, String> statuses = states == null ? Map.of() : states.stream()
                    .filter(state -> state != null && state.quest_id != null)
                    .collect(Collectors.toMap(state -> normalizeId(state.quest_id), state -> state.status, (a, b) -> a));

            Set<String> completedIds = statuses.entrySet().stream()
                    .filter(entry -> "COMPLETED".equalsIgnoreCase(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            return allQuests.stream()
                    .filter(quest -> quest != null && interactionAction(quest,
                            statuses.getOrDefault(normalizeId(quest.questId), "AVAILABLE"), finalNpcId, npcId) != null)
                    .filter(quest -> statuses.containsKey(normalizeId(quest.questId))
                            || prerequisitesMetNormalized(quest, completedIds))
                    .map(quest -> toInteraction(player, quest,
                            statuses.getOrDefault(normalizeId(quest.questId), "AVAILABLE"),
                            interactionAction(quest, statuses.getOrDefault(normalizeId(quest.questId), "AVAILABLE"),
                                    finalNpcId, npcId)))
                    .toList();
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Impossible de charger les interactions du PNJ {}", npcId, e);
            throw new IllegalStateException("Interactions PNJ indisponibles", e);
        }
    }

    private static Set<String> getAvailableNpcSpawnIds(ServerPlayer player) throws Exception {
        CatalogCache catalog = getCatalogCached();
        List<PlayerQuestStateModel> states = getPlayerQuestsCached(player);
        Set<String> knownQuestIds = states.stream()
                .filter(state -> state != null && state.quest_id != null)
                .map(state -> normalizeId(state.quest_id))
                .collect(Collectors.toSet());
        Set<String> completedQuestIds = states.stream()
                .filter(state -> state != null && "COMPLETED".equalsIgnoreCase(state.status))
                .map(state -> normalizeId(state.quest_id))
                .collect(Collectors.toSet());

        List<QuestModel> availableQuests = catalog.quests().stream()
                .filter(quest -> quest != null && quest.questId != null && !quest.questId.isBlank())
                .filter(quest -> !knownQuestIds.contains(normalizeId(quest.questId)))
                .filter(quest -> prerequisitesMetNormalized(quest, completedQuestIds))
                .toList();

        Set<String> availableSpawnIds = new HashSet<>();
        for (NpcSpawnModel spawn : catalog.spawns()) {
            if (spawn == null || spawn.spawnId == null || spawn.spawnId.isBlank()) continue;
            boolean available = availableQuests.stream()
                    .anyMatch(quest -> spawnOffersQuest(spawn, quest, catalog.npcs()));
            if (available) availableSpawnIds.add(spawn.spawnId);
        }
        return availableSpawnIds;
    }

    private static boolean spawnOffersQuest(NpcSpawnModel spawn, QuestModel quest, List<NpcModel> npcs) {
        return equalsIgnoreCase(spawn.npcId, quest.getStartNpcId());
    }

    private static boolean prerequisitesMetNormalized(QuestModel quest, Set<String> completedIds) {
        boolean allMet = quest.prerequisitesAll == null
                || quest.prerequisitesAll.stream().map(QuestObjectiveHandler::normalizeId).allMatch(completedIds::contains);
        boolean anyMet = quest.prerequisitesAny == null || quest.prerequisitesAny.isEmpty()
                || quest.prerequisitesAny.stream().map(QuestObjectiveHandler::normalizeId).anyMatch(completedIds::contains);
        boolean parentMet = quest.parentId == null || quest.parentId.isBlank()
                || completedIds.contains(normalizeId(quest.parentId));
        return allMet && anyMet && parentMet;
    }

    private static String interactionAction(QuestModel quest, String status, String... npcIdentities) {
        if (quest == null || npcIdentities == null) return null;
        if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) return null;
        for (String identity : npcIdentities) {
            if ("AVAILABLE".equalsIgnoreCase(status) && equalsIgnoreCase(identity, quest.getStartNpcId())) return "START";
            if (!"AVAILABLE".equalsIgnoreCase(status) && equalsIgnoreCase(identity, quest.getCompletionNpcId())) return "COMPLETE";
            if (!"AVAILABLE".equalsIgnoreCase(status) && quest.objectives != null && quest.objectives.stream()
                    .anyMatch(objective -> objective != null
                            && equalsIgnoreCase(identity, objective.getTargetNpcId()))) return "OBJECTIVE";
        }
        return null;
    }

    private static QuestModel getQuestDetailsCached(String questId) throws Exception {
        long now = System.currentTimeMillis();
        String cacheKey = normalizeId(questId);
        CachedQuest cached = QUEST_DETAILS_CACHE.get(cacheKey);
        if (cached != null && now - cached.loadedAt() < QUEST_DETAILS_CACHE_MS) return cached.quest();

        QuestModel quest = ApiClient.getQuestDetails(questId);
        if (quest != null) QUEST_DETAILS_CACHE.put(cacheKey, new CachedQuest(quest, now));
        return quest;
    }

    private static CatalogCache getCatalogCached() throws Exception {
        long now = System.currentTimeMillis();
        CatalogCache cached = catalogCache;
        if (cached != null && now - cached.loadedAt() < CATALOG_CACHE_MS) return cached;

        synchronized (QuestObjectiveHandler.class) {
            cached = catalogCache;
            if (cached != null && now - cached.loadedAt() < CATALOG_CACHE_MS) return cached;

            List<NpcSpawnModel> cachedSpawns = cached == null ? List.of() : cached.spawns();
            List<NpcModel> cachedNpcs = cached == null ? List.of() : cached.npcs();
            List<QuestModel> cachedQuests = cached == null ? List.of() : cached.quests();
            CompletableFuture<List<NpcSpawnModel>> spawnsFuture = CompletableFuture.supplyAsync(() -> loadOrFallback(
                    () -> ApiClient.getNpcSpawns(null, true), "spawns de PNJ", cachedSpawns));
            CompletableFuture<List<NpcModel>> npcsFuture = CompletableFuture.supplyAsync(() -> loadOrFallback(
                    ApiClient::getNpcs, "modèles de PNJ", cachedNpcs));
            CompletableFuture<List<QuestModel>> questsFuture = CompletableFuture.supplyAsync(() -> loadOrFallback(
                    () -> ApiClient.getAllQuests(""), "catalogue de quêtes", cachedQuests));

            CompletableFuture.allOf(spawnsFuture, npcsFuture, questsFuture).join();
            catalogCache = new CatalogCache(
                    List.copyOf(spawnsFuture.join()),
                    List.copyOf(npcsFuture.join()),
                    List.copyOf(questsFuture.join()),
                    now
            );
            return catalogCache;
        }
    }

    private static List<PlayerQuestStateModel> getPlayerQuestsCached(ServerPlayer player) throws Exception {
        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUUID();
        CachedPlayerQuests cached = PLAYER_QUEST_CACHE.get(playerUuid);
        if (cached != null && now - cached.loadedAt() < PLAYER_QUEST_CACHE_MS) return cached.states();

        try {
            PlayerModel profile = ApiClient.getPlayer(playerUuid.toString());
            String playerId = profile != null && profile.id != null ? profile.id : playerUuid.toString();
            List<PlayerQuestStateModel> states = ApiClient.getPlayerQuests(playerId);
            List<PlayerQuestStateModel> result = states == null ? List.of() : List.copyOf(states);
            PLAYER_QUEST_CACHE.put(playerUuid, new CachedPlayerQuests(result, now));
            return result;
        } catch (Exception e) {
            if (cached != null) {
                MedievalCoin.LOGGER.warn("API joueur indisponible, dernier cache conserve pour {}",
                        player.getName().getString());
                return cached.states();
            }
            throw e;
        }
    }

    private static <T> List<T> loadOrFallback(ApiListLoader<T> loader, String label, List<T> fallback) {
        try {
            List<T> result = loader.load();
            return result == null ? fallback : result;
        } catch (Exception e) {
            MedievalCoin.LOGGER.warn("Impossible de charger {} : {}. Dernier cache conserve.", label,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return fallback;
        }
    }

    private static void addNormalizedQuestIds(Set<String> destination, List<String> questIds) {
        if (questIds == null) return;
        for (String questId : questIds) {
            String normalized = normalizeId(questId);
            if (!normalized.isEmpty()) destination.add(normalized);
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static NpcQuestInteractionModel toInteraction(ServerPlayer player, QuestModel quest, String status, String npcAction) {
        CompoundTag progress = getQuestProgress(player, quest.questId);
        List<NpcQuestInteractionModel.ObjectiveProgress> objectives = new ArrayList<>();
        if (quest.objectives != null) {
            for (int i = 0; i < quest.objectives.size(); i++) {
                objectives.add(new NpcQuestInteractionModel.ObjectiveProgress(
                        i, objectiveDescription(quest.objectives.get(i)), progress.getBoolean(Integer.toString(i))));
            }
        }
        List<String> missing = getMissingObjectives(player, quest.questId, quest);
        return new NpcQuestInteractionModel(quest.questId, quest.name, status,
                quest.category, quest.type, quest.getDescription(), quest.parentId,
                quest.prerequisitesAll, quest.prerequisitesAny,
                localized(quest.beginText), localized(quest.endText), objectives,
                missing.isEmpty() ? "" : String.join(", ", missing), quest.rewards, quest.money, quest.xp,
                npcAction, "COMPLETE".equals(npcAction) && "IN_PROGRESS".equalsIgnoreCase(status) && missing.isEmpty());
    }

    private static List<String> getMissingObjectives(ServerPlayer player, String questId, QuestModel quest) {
        List<String> missing = new ArrayList<>();
        if (quest.objectives == null) return missing;
        CompoundTag progress = getQuestProgress(player, questId);
        for (int i = 0; i < quest.objectives.size(); i++) {
            if (!progress.getBoolean(Integer.toString(i))) missing.add(objectiveDescription(quest.objectives.get(i)));
        }
        return missing;
    }

    private static void evaluateQuestObjectivesForNpc(
            ServerPlayer player,
            PlayerQuestStateModel state,
            QuestModel quest,
            NpcIdentity interactedNpc) {
        if (quest == null || quest.objectives == null || quest.objectives.isEmpty()) return;
        String questId = firstNonBlank(state.quest_id, quest.questId);
        if (questId == null) return;

        CompoundTag progress = getQuestProgress(player, questId);
        boolean changed = false;
        for (int index = 0; index < quest.objectives.size(); index++) {
            QuestModel.Objective objective = quest.objectives.get(index);
            if (objective == null || progress.getBoolean(Integer.toString(index))) continue;

            String type = normalize(objective.type);
            if (!"item".equals(type) && !"items".equals(type)) continue;

            boolean expectedNpc = interactedNpc == null
                    || interactedNpc.matches(objective.getTargetNpcId())
                    || interactedNpc.matches(quest.getCompletionNpcId());
            if (!expectedNpc) continue;

            List<QuestModel.ItemRequirement> requirements = itemRequirements(objective);
            if (!requirements.isEmpty() && hasRequiredItems(player, requirements)) {
                removeRequiredItems(player, requirements);
                progress.putBoolean(Integer.toString(index), true);
                changed = true;
                notifyObjective(player, objective);
            }
        }

        if (changed) {
            saveQuestProgress(player, questId, progress);
            syncProgress(player, ACTIVE_QUEST_CACHE.getOrDefault(player.getUUID(), List.of()));
        }
    }

    private static String objectiveDescription(QuestModel.Objective objective) {
        if (objective == null) return "Objectif";
        String customDescription = objective.description == null ? "" : objective.description.trim();
        return switch (normalize(objective.type)) {
            case "talk", "npc" -> customDescription.isBlank()
                    ? "Parler au PNJ " + firstNonBlank(objective.getTargetNpcId(), "{npc}")
                    : customDescription;
            case "item", "items" -> itemObjectiveDescription(objective, customDescription);
            case "location" -> locationObjectiveDescription(objective, customDescription);
            case "construction", "build" -> customDescription.isBlank() ? "Construction" : customDescription;
            case "rp", "roleplay", "role_play" -> customDescription.isBlank() ? "RP" : customDescription;
            default -> customDescription.isBlank() ? "Objectif" : customDescription;
        };
    }

    private static String itemName(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return value + " (objet inconnu)";
        return new ItemStack(ForgeRegistries.ITEMS.getValue(id)).getHoverName().getString();
    }

    private static String itemObjectiveDescription(QuestModel.Objective objective, String fallback) {
        List<QuestModel.ItemRequirement> requirements = itemRequirements(objective);
        if (requirements.isEmpty()) {
            return fallback.isBlank() ? "Objectif à configurer : aucun objet demandé n’est renseigné" : fallback;
        }
        String items = requirements.stream()
                .filter(item -> item != null && item.itemId != null && !item.itemId.isBlank())
                .map(item -> requiredCount(item) + " × " + itemName(item.itemId))
                .collect(Collectors.joining(", "));
        return items.isBlank() ? (fallback.isBlank() ? "Objectif à configurer : aucun objet demandé n’est renseigné" : fallback) : "Apporter : " + items;
    }

    private static String locationObjectiveDescription(QuestModel.Objective objective, String fallback) {
        if (objective.coord == null || objective.coord.isBlank()) {
            return fallback.isBlank() ? "Atteindre le lieu indiqué" : fallback;
        }
        String coordinates = objective.coord.replace(" r:", " (rayon : ");
        return "Se rendre à " + coordinates + (objective.coord.contains(" r:") ? " blocs)" : "");
    }

    private static String localized(Map<String, String> text) {
        if (text == null) return "";
        return firstNonBlank(text.get("fr"), text.get("en"), "");
    }

    private static void grantRewards(ServerPlayer player, QuestModel quest) throws Exception {
        fr.renblood.medievalcoins.bank.QuestRewardService.grant(player,quest.questId);
    }

    private static void syncProgress(ServerPlayer player, List<PlayerQuestStateModel> activeQuests) {
        Map<String, List<Integer>> completedObjectives = new ConcurrentHashMap<>();
        for (PlayerQuestStateModel state : activeQuests) {
            if (state == null || state.quest_id == null || state.questDetails == null || state.questDetails.objectives == null) continue;
            CompoundTag progress = getQuestProgress(player, state.quest_id);
            List<Integer> indexes = new ArrayList<>();
            for (int index = 0; index < state.questDetails.objectives.size(); index++) {
                if (progress.getBoolean(Integer.toString(index))) indexes.add(index);
            }
            completedObjectives.put(state.quest_id, indexes);
        }
        MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new QuestProgressMessage(completedObjectives));
    }

    private static boolean isAtLocation(BlockPos playerPos, String coordinateText) {
        List<Double> coordinates = new ArrayList<>(3);
        Matcher matcher = COORDINATE_PATTERN.matcher(coordinateText == null ? "" : coordinateText);
        while (matcher.find() && coordinates.size() < 3) {
            coordinates.add(Double.parseDouble(matcher.group()));
        }
        if (coordinates.size() != 3) return false;

        BlockPos target = BlockPos.containing(coordinates.get(0), coordinates.get(1), coordinates.get(2));
        return Math.abs(playerPos.getX() - target.getX()) <= 1
                && Math.abs(playerPos.getY() - target.getY()) <= 1
                && Math.abs(playerPos.getZ() - target.getZ()) <= 1;
    }

    private static boolean hasRequiredItems(ServerPlayer player, List<QuestModel.ItemRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return false;
        for (QuestModel.ItemRequirement requirement : requirements) {
            Item item = resolveItem(requirement);
            int requiredCount = requiredCount(requirement);
            if (item == null || requiredCount <= 0 || countItem(player, item) < requiredCount) return false;
        }
        return true;
    }

    private static void removeRequiredItems(ServerPlayer player, List<QuestModel.ItemRequirement> requirements) {
        for (QuestModel.ItemRequirement requirement : requirements) {
            Item item = resolveItem(requirement);
            int remaining = requiredCount(requirement);
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(item)) continue;
                int removed = Math.min(stack.getCount(), remaining);
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Item resolveItem(QuestModel.ItemRequirement requirement) {
        if (requirement == null || requirement.itemId == null || requirement.itemId.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(requirement.itemId);
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    private static int requiredCount(QuestModel.ItemRequirement requirement) {
        return requirement == null ? 0 : Math.max(1, requirement.count);
    }

    private static List<QuestModel.ItemRequirement> itemRequirements(QuestModel.Objective objective) {
        if (objective == null) return List.of();
        return objective.getItemRequirements().stream().filter(row -> row != null && row.itemId != null && !row.itemId.isBlank()).toList();
    }

    private static String firstPrimitive(com.google.gson.JsonObject object, String... keys) {
        for (String key : keys) {
            com.google.gson.JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) return value.getAsString();
        }
        return null;
    }

    private static Integer firstInteger(com.google.gson.JsonObject object, String... keys) {
        for (String key : keys) {
            com.google.gson.JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) return value.getAsInt();
        }
        return null;
    }

    private static String resolveBackendPlayerId(ServerPlayer player, PlayerQuestStateModel state) throws Exception {
        if (state.player_id != null && !state.player_id.isBlank()) return state.player_id;
        PlayerModel profile = ApiClient.getPlayer(player.getUUID().toString());
        if (profile != null && profile.id != null && !profile.id.isBlank()) return profile.id;
        if (profile != null && profile.id_minecraft != null && !profile.id_minecraft.isBlank()) return profile.id_minecraft;
        return player.getUUID().toString();
    }

    private static CompoundTag getQuestProgress(ServerPlayer player, String questId) {
        CompoundTag root = player.getPersistentData().getCompound(PROGRESS_TAG);
        return root.getCompound(questId);
    }

    private static void saveQuestProgress(ServerPlayer player, String questId, CompoundTag progress) {
        CompoundTag root = player.getPersistentData().getCompound(PROGRESS_TAG);
        root.put(questId, progress);
        player.getPersistentData().put(PROGRESS_TAG, root);
    }

    private static void clearInactiveQuestProgress(ServerPlayer player, List<PlayerQuestStateModel> activeQuests) {
        Set<String> activeIds = ConcurrentHashMap.newKeySet();
        for (PlayerQuestStateModel state : activeQuests) {
            if (state != null && state.quest_id != null) activeIds.add(state.quest_id);
        }

        CompoundTag root = player.getPersistentData().getCompound(PROGRESS_TAG);
        for (String savedQuestId : Set.copyOf(root.getAllKeys())) {
            if (!activeIds.contains(savedQuestId)) root.remove(savedQuestId);
        }
        player.getPersistentData().put(PROGRESS_TAG, root);
    }

    private static boolean allObjectivesComplete(CompoundTag progress, int objectiveCount) {
        for (int index = 0; index < objectiveCount; index++) {
            if (!progress.getBoolean(Integer.toString(index))) return false;
        }
        return true;
    }

    private static void notifyObjective(ServerPlayer player, QuestModel.Objective objective) {
        String description = firstNonBlank(objective.description, objective.type);
        player.sendSystemMessage(Component.literal("Objectif accompli : " + description).withStyle(ChatFormatting.GOLD));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private record NpcIdentity(String spawnId, String npcId, String name) {
        private boolean matches(String target) {
            return target != null && (equalsIgnoreCase(target, spawnId)
                    || equalsIgnoreCase(target, npcId)
                    || equalsIgnoreCase(target, name));
        }
    }

    private record CachedQuest(QuestModel quest, long loadedAt) {
    }

    private record CachedPlayerQuests(List<PlayerQuestStateModel> states, long loadedAt) {
    }

    private record CatalogCache(List<NpcSpawnModel> spawns, List<NpcModel> npcs, List<QuestModel> quests, long loadedAt) {
    }

    @FunctionalInterface
    private interface ApiListLoader<T> {
        List<T> load() throws Exception;
    }
}

