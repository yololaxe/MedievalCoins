package fr.renblood.medievalcoins.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.*;
import fr.renblood.medievalcoins.config.ModConfig;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class ApiClient {
    // On utilise le GSON configuré de PlayerModel pour bénéficier des adapters personnalisés
    private static final Gson GSON = PlayerModel.GSON;

    // --- NPC & SPAWNS ---

    public static List<NpcModel> getNpcs() throws Exception {
        List<NpcModel> cached = ApiCache.get("npcs");
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/list/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<NpcModel>>(){}.getType();
        List<NpcModel> result = parseListResponse(json, listType, "npcs", "results", "data");
        ApiCache.put("npcs", result, cacheTtl());
        return result;
    }

    public static List<NpcSpawnModel> getNpcSpawns(String worldName) throws Exception {
        return getNpcSpawns(worldName, true);
    }

    public static List<NpcSpawnModel> getNpcSpawns(String worldName, boolean includeInactive) throws Exception {
        ModConfig cfg = ModConfig.load();
        Type listType = new TypeToken<List<NpcSpawnModel>>(){}.getType();
        List<NpcSpawnModel> spawns = getAllNpcSpawns(cfg, listType, includeInactive);
        if (worldName != null && !worldName.isBlank()) {
            spawns = spawns.stream()
                    .filter(spawn -> spawn != null && matchesWorld(spawn, worldName))
                    .toList();
        }

        if (includeInactive) return spawns;
        return spawns.stream().filter(spawn -> spawn != null && spawn.active).toList();
    }

    private static List<NpcSpawnModel> getAllNpcSpawns(ModConfig cfg, Type listType, boolean includeInactive) throws Exception {
        String cacheKey = "npc-spawns:" + includeInactive;
        List<NpcSpawnModel> cached = ApiCache.get(cacheKey);
        if (cached != null) return cached;
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/spawns/list/") + "?include_inactive=" + includeInactive;
        List<NpcSpawnModel> result = parseNpcSpawns(sendGetRequest(endpoint, cfg.apiKey), listType);
        ApiCache.put(cacheKey, result, cacheTtl());
        return result;
    }

    private static List<NpcSpawnModel> parseNpcSpawns(String json, Type listType) {
        return parseListResponse(json, listType, "spawns", "npc_spawns", "results", "data");
    }

    private static boolean matchesWorld(NpcSpawnModel spawn, String worldName) {
        return worldName.equalsIgnoreCase(spawn.world == null ? "" : spawn.world)
                || worldName.equalsIgnoreCase(spawn.dimension == null ? "" : spawn.dimension);
    }

    public static boolean createNpc(NpcModel npc) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/create/");
        
        // Génération automatique d'ID si manquant (pour éviter l'erreur 400)
        if (npc.npcId == null || npc.npcId.trim().isEmpty()) {
            String baseName = (npc.name != null && !npc.name.isEmpty()) ? npc.name.toLowerCase().replaceAll("[^a-z0-9]", "_") : "npc";
            npc.npcId = baseName + "_" + UUID.randomUUID().toString().substring(0, 6);
            MedievalCoin.LOGGER.warn("API: npcId manquant pour le NPC '{}'. ID généré automatiquement : {}", npc.name, npc.npcId);
        }

        JsonObject body = GSON.toJsonTree(npc).getAsJsonObject();
        
        if (MedievalCoin.DEBUG_MODE) {
            MedievalCoin.LOGGER.info("API: Sending NPC create request to " + endpoint + " : " + body.toString());
        }
        
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        ApiCache.invalidatePrefix("npcs");
        return true;
    }

    public static boolean createNpcSpawn(NpcSpawnModel spawn) throws Exception {
        return createNpcSpawn(resolveSingleOnlinePlayer(), spawn);
    }

    public static boolean createNpcSpawn(ServerPlayer player, NpcSpawnModel spawn) throws Exception {
        if (player == null) {
            throw new IllegalArgumentException("joueur Minecraft obligatoire pour creer un spawn PNJ");
        }
        if (spawn == null) {
            throw new IllegalArgumentException("spawn est obligatoire");
        }
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/spawns/create/");
        
        // Génération automatique d'ID si manquant
        if (spawn.spawnId == null || spawn.spawnId.trim().isEmpty()) {
            spawn.spawnId = "";
            MedievalCoin.LOGGER.warn("API: spawnId manquant. ID généré automatiquement : {}", spawn.spawnId);
        }
        
        // Vérification du npc_id
        if (spawn.spawnId == null || spawn.spawnId.trim().isEmpty()) {
            throw new IllegalArgumentException("spawnId est obligatoire et doit etre stable");
        }

        if (spawn.npcId == null || spawn.npcId.trim().isEmpty()) {
            MedievalCoin.LOGGER.error("API: npcId manquant pour le spawn '{}'. Impossible de créer le spawn.", spawn.spawnId);
            throw new IllegalArgumentException("npcId est obligatoire pour créer un spawn");
        }

        JsonObject body = GSON.toJsonTree(spawn).getAsJsonObject();
        
        if (MedievalCoin.DEBUG_MODE) {
            MedievalCoin.LOGGER.info("API: Sending NPC Spawn create request: " + body.toString());
        }

        sendMinecraftPostRequest(endpoint, cfg.apiKey, player.getUUID().toString(), body);
        ApiCache.invalidatePrefix("npc-spawns:");
        return true;
    }

    private static ServerPlayer resolveSingleOnlinePlayer() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().size() != 1) {
            throw new IllegalStateException(
                    "Impossible d'identifier l'auteur du spawn PNJ; utilisez createNpcSpawn(ServerPlayer, NpcSpawnModel)"
            );
        }
        return server.getPlayerList().getPlayers().get(0);
    }

    public static boolean addQuestToNpc(String npcId, String questId) throws Exception {
        return updateNpcQuestAssignment(npcId, questId, true);
    }

    public static boolean removeQuestFromNpc(String npcId, String questId) throws Exception {
        return updateNpcQuestAssignment(npcId, questId, false);
    }

    private static boolean updateNpcQuestAssignment(String npcId, String questId, boolean assign) throws Exception {
        if (assign && (npcId == null || npcId.isBlank())) throw new IllegalArgumentException("npc_id manquant");
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("quest_id manquant");

        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/" + encodePathSegment(questId) + "/npc/start/");
        JsonObject body = new JsonObject();
        if (assign) body.addProperty("startNpcId", npcId);
        else body.add("startNpcId", com.google.gson.JsonNull.INSTANCE);

        sendPutRequest(endpoint, cfg.apiKey, body);
        ApiCache.invalidatePrefix("quests:");
        ApiCache.invalidatePrefix("quest:");
        return true;
    }

    // --- QUÊTES ---

    public static List<QuestModel> getAllQuests(String category) throws Exception {
        String cacheKey = "quests:" + (category == null ? "" : category);
        List<QuestModel> cached = ApiCache.get(cacheKey);
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/list/");
        if (category != null && !category.isEmpty()) {
            endpoint += "?category=" + category;
        }
        
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<QuestModel>>(){}.getType();
        List<QuestModel> result = parseListResponse(json, listType, "quests", "results", "data");
        ApiCache.put(cacheKey, result, cacheTtl());
        return result;
    }

    public static List<PlayerQuestStateModel> getActiveQuests(String mcId, String category) throws Exception {
        String cacheKey = "active-quests:" + mcId + ":" + (category == null ? "" : category);
        List<PlayerQuestStateModel> cached = ApiCache.get(cacheKey);
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/minecraft/" + mcId + "/active/");
        if (category != null && !category.isEmpty()) {
            endpoint += "?category=" + category;
        }
        
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<PlayerQuestStateModel>>(){}.getType();
        List<PlayerQuestStateModel> result = parseListResponse(json, listType, "quests", "active_quests", "results", "data");
        ApiCache.put(cacheKey, result, cacheTtl());
        return result;
    }

    public static List<PlayerQuestStateModel> getPlayerQuests(String playerId) throws Exception {
        String cacheKey = "player-quests:" + playerId;
        List<PlayerQuestStateModel> cached = ApiCache.get(cacheKey);
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/player/" + encodePathSegment(playerId) + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<PlayerQuestStateModel>>(){}.getType();
        List<PlayerQuestStateModel> result = parseListResponse(json, listType, "quests", "results", "data");
        ApiCache.put(cacheKey, result, cacheTtl());
        return result;
    }

    public static QuestModel getQuestDetails(String questId) throws Exception {
        QuestModel cached = ApiCache.get("quest:" + questId);
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/" + questId + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        QuestModel result = GSON.fromJson(json, QuestModel.class);
        ApiCache.put("quest:" + questId, result, cacheTtl());
        return result;
    }

    public static boolean updateQuestNpc(String questId, String npcId, String npcName) throws Exception {
        return updateQuestStartNpc(questId, npcId);
    }

    public static boolean updateQuestStartNpc(String questId, String npcId) throws Exception {
        return updateNpcQuestAssignment(npcId, questId, npcId != null && !npcId.isBlank());
    }

    // --- JOUEURS & ÉCONOMIE ---

    public static boolean completeQuest(String playerId, String questId) throws Exception {
        if (playerId == null || playerId.isEmpty()) {
            throw new IllegalArgumentException("player_id manquant pour valider la quete");
        }

        ModConfig cfg = ModConfig.load();
        JsonObject body = new JsonObject();
        body.addProperty("quest_id", questId);
        body.addProperty("status", "COMPLETED");

        String endpoint = buildUrl(cfg.apiUrl, "/quests/player/" + playerId + "/update/");
        sendPostRequest(endpoint, cfg.apiKey, body);
        ApiCache.invalidatePrefix("active-quests:");
        ApiCache.invalidatePrefix("player-quests:" + playerId);
        return true;
    }

    public static boolean startQuest(String playerId, String questId) throws Exception {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("player_id manquant");
        ModConfig cfg = ModConfig.load();
        JsonObject body = new JsonObject();
        body.addProperty("quest_id", questId);
        body.addProperty("status", "IN_PROGRESS");
        sendPostRequest(buildUrl(cfg.apiUrl, "/quests/player/" + encodePathSegment(playerId) + "/update/"), cfg.apiKey, body);
        ApiCache.invalidatePrefix("active-quests:");
        ApiCache.invalidatePrefix("player-quests:" + playerId);
        return true;
    }

    public static PlayerModel getPlayer(String mcId) throws Exception {
        PlayerModel cached = ApiCache.get("player:" + mcId);
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/getByMinecraft/" + mcId + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        PlayerModel result = PlayerModel.fromJson(json);
        ApiCache.put("player:" + mcId, result, cacheTtl());
        return result;
    }

    public static List<PlayerModel> getPlayers(String rank) throws Exception {
        String cacheKey = "players:" + rank;
        List<PlayerModel> cached = ApiCache.get(cacheKey);
        if (cached != null) return cached;
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/getPlayers/" + rank + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<PlayerModel>>(){}.getType();
        List<PlayerModel> result = GSON.fromJson(json, listType);
        ApiCache.put(cacheKey, result, cacheTtl());
        return result;
    }

    public static JsonObject manageJobXp(String mcId, String action, String jobName, int amount) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("action", action);
        body.addProperty("job", jobName);
        body.addProperty("amount", amount);
        
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/manage_job_xp/" + mcId + "/");
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        ApiCache.invalidatePrefix("player:" + mcId);
        ApiCache.invalidatePrefix("players:");
        return GSON.fromJson(json, JsonObject.class);
    }

    public static int deposit(String playerId, int amount) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("amount", amount);
        
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/deposit/" + playerId + "/");
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        ApiCache.invalidatePrefix("player:");
        JsonObject resp = GSON.fromJson(json, JsonObject.class);
        return resp.get("new_balance").getAsInt();
    }

    public static int withdraw(String playerId, int coinType, int amount) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("coin_type", coinType);
        body.addProperty("amount", amount);
        
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/withdraw/" + playerId + "/");
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        ApiCache.invalidatePrefix("player:");
        JsonObject resp = GSON.fromJson(json, JsonObject.class);
        return resp.get("new_balance").getAsInt();
    }

    // --- HELPERS HTTP ---
    
    private static String buildUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Duration cacheTtl() {
        return Duration.ofSeconds(ModConfig.load().apiCacheTtlSeconds);
    }

    private static <T> T parseListResponse(String json, Type listType, String... arrayKeys) {
        JsonElement root = JsonParser.parseString(json);
        if (root == null || root.isJsonNull()) {
            return GSON.fromJson("[]", listType);
        }
        if (root.isJsonArray()) {
            return GSON.fromJson(root, listType);
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : arrayKeys) {
                JsonElement value = obj.get(key);
                if (value != null && value.isJsonArray()) {
                    return GSON.fromJson(value, listType);
                }
            }
        }
        MedievalCoin.LOGGER.warn("API: expected a list response but got: {}", json);
        return GSON.fromJson("[]", listType);
    }

    private static String sendGetRequest(String endpoint, String apiKey) throws Exception {
        return ApiHttpClient.json("GET", endpoint, null, apiKey);
    }

    private static String sendPostRequest(String endpoint, String apiKey, JsonObject body) throws Exception {
        return sendJsonRequest("POST", endpoint, apiKey, body);
    }

    private static String sendMinecraftPostRequest(String endpoint, String apiKey, String minecraftUuid, JsonObject body) throws Exception {
        return ApiHttpClient.minecraftPost(endpoint, body, minecraftUuid, apiKey);
    }

    private static String sendPutRequest(String endpoint, String apiKey, JsonObject body) throws Exception {
        return sendJsonRequest("PUT", endpoint, apiKey, body);
    }

    private static String sendJsonRequest(String method, String endpoint, String apiKey, JsonObject body) throws Exception {
        return ApiHttpClient.json(method, endpoint, body, apiKey);
    }
}
