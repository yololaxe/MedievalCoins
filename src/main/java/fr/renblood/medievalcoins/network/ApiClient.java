package fr.renblood.medievalcoins.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.*;
import fr.renblood.medievalcoins.config.ModConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

public class ApiClient {
    // On utilise le GSON configuré de PlayerModel pour bénéficier des adapters personnalisés
    private static final Gson GSON = PlayerModel.GSON;

    // --- NPC & SPAWNS ---

    public static List<NpcModel> getNpcs() throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/list/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<NpcModel>>(){}.getType();
        return GSON.fromJson(json, listType);
    }

    public static List<NpcSpawnModel> getNpcSpawns(String worldName) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/spawns/list/");
        if (worldName != null && !worldName.isEmpty()) {
             endpoint = buildUrl(cfg.apiUrl, "/npcs/spawns/world/" + worldName + "/");
        }
        
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<NpcSpawnModel>>(){}.getType();
        return GSON.fromJson(json, listType);
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
        return true;
    }

    public static boolean createNpcSpawn(NpcSpawnModel spawn) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/spawns/create/");
        
        // Génération automatique d'ID si manquant
        if (spawn.spawnId == null || spawn.spawnId.trim().isEmpty()) {
            spawn.spawnId = "spawn_" + UUID.randomUUID().toString().substring(0, 8);
            MedievalCoin.LOGGER.warn("API: spawnId manquant. ID généré automatiquement : {}", spawn.spawnId);
        }
        
        // Vérification du npc_id
        if (spawn.npcId == null || spawn.npcId.trim().isEmpty()) {
            MedievalCoin.LOGGER.error("API: npcId manquant pour le spawn '{}'. Impossible de créer le spawn.", spawn.spawnId);
            throw new IllegalArgumentException("npcId est obligatoire pour créer un spawn");
        }

        JsonObject body = GSON.toJsonTree(spawn).getAsJsonObject();
        
        if (MedievalCoin.DEBUG_MODE) {
            MedievalCoin.LOGGER.info("API: Sending NPC Spawn create request: " + body.toString());
        }

        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        return true;
    }

    public static boolean addQuestToNpc(String npcId, String questId) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/" + npcId + "/quests/add/");
        
        JsonObject body = new JsonObject();
        body.addProperty("quest_id", questId);
        
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        return true;
    }

    public static boolean removeQuestFromNpc(String npcId, String questId) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/npcs/" + npcId + "/quests/remove/");
        
        JsonObject body = new JsonObject();
        body.addProperty("quest_id", questId);
        
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        return true;
    }

    // --- QUÊTES ---

    public static List<QuestModel> getAllQuests(String category) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/list/");
        if (category != null && !category.isEmpty()) {
            endpoint += "?category=" + category;
        }
        
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<QuestModel>>(){}.getType();
        return GSON.fromJson(json, listType);
    }

    public static List<PlayerQuestStateModel> getActiveQuests(String mcId, String category) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/minecraft/" + mcId + "/active/");
        if (category != null && !category.isEmpty()) {
            endpoint += "?category=" + category;
        }
        
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<PlayerQuestStateModel>>(){}.getType();
        return GSON.fromJson(json, listType);
    }

    public static QuestModel getQuestDetails(String questId) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/quests/" + questId + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        return GSON.fromJson(json, QuestModel.class);
    }

    // --- JOUEURS & ÉCONOMIE ---

    public static PlayerModel getPlayer(String mcId) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/getByMinecraft/" + mcId + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        return PlayerModel.fromJson(json);
    }

    public static List<PlayerModel> getPlayers(String rank) throws Exception {
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/getPlayers/" + rank + "/");
        String json = sendGetRequest(endpoint, cfg.apiKey);
        Type listType = new TypeToken<List<PlayerModel>>(){}.getType();
        return GSON.fromJson(json, listType);
    }

    public static JsonObject manageJobXp(String mcId, String action, String jobName, int amount) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("action", action);
        body.addProperty("job", jobName);
        body.addProperty("amount", amount);
        
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/manage_job_xp/" + mcId + "/");
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
        return GSON.fromJson(json, JsonObject.class);
    }

    public static int deposit(String playerId, int amount) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("amount", amount);
        
        ModConfig cfg = ModConfig.load();
        String endpoint = buildUrl(cfg.apiUrl, "/players/deposit/" + playerId + "/");
        String json = sendPostRequest(endpoint, cfg.apiKey, body);
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

    private static String sendGetRequest(String endpoint, String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("API key non configurée");
        
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String errorMsg = readErrorStream(conn);
            throw new RuntimeException("HTTP " + code + " sur " + endpoint + " : " + errorMsg);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String sendPostRequest(String endpoint, String apiKey, JsonObject body) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("API key non configurée");

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(GSON.toJson(body).getBytes());
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String errorMsg = readErrorStream(conn);
            throw new RuntimeException("HTTP " + code + " sur " + endpoint + " : " + errorMsg);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
    
    private static String readErrorStream(HttpURLConnection conn) {
        try {
            InputStream es = conn.getErrorStream();
            if (es == null) return "No error details";
            try (BufferedReader in = new BufferedReader(new InputStreamReader(es))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            return "Failed to read error stream: " + e.getMessage();
        }
    }
}
