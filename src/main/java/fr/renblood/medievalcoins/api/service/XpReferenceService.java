package fr.renblood.medievalcoins.api.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.XpReferenceData;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiHttpClient;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class XpReferenceService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("medieval_coins")
            .resolve("xp_reference_items_cache.json");
    private static final List<String> ENDPOINTS = List.of(
            "/minecraft/xp/reference-items/",
            "/minecraft/jobs/xp-references/"
    );

    private static volatile List<XpReferenceData> references = List.of();
    private static volatile String lastLoadedAt = "jamais";

    private XpReferenceService() {
    }

    public static void initialize() {
        loadCache();
    }

    public static CompletableFuture<List<XpReferenceData>> reloadAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastFailure = null;
            for (String endpoint : ENDPOINTS) {
                try {
                    List<XpReferenceData> loaded = fetch(endpoint);
                    references = loaded;
                    lastLoadedAt = Instant.now().toString();
                    saveCache();
                    MedievalCoin.LOGGER.info("References XP chargees depuis {}: {}", endpoint, loaded.size());
                    return loaded;
                } catch (Exception exception) {
                    lastFailure = exception;
                    MedievalCoin.LOGGER.warn("Endpoint references XP indisponible {}: {}", endpoint, exception.getMessage());
                }
            }
            loadCache();
            if (!references.isEmpty()) return references;
            throw new IllegalStateException("Aucun endpoint de references XP disponible", lastFailure);
        }, fr.renblood.medievalcoins.network.ApiExecutor.executor());
    }

    public static List<XpReferenceData> getReferences() {
        if (references.isEmpty()) loadCache();
        return references;
    }

    public static int getReferenceXp(String action, String job, String itemId) {
        return getReferences().stream()
                .filter(reference -> reference.matches(action, job, itemId))
                .mapToInt(XpReferenceData::xpAmount)
                .findFirst()
                .orElse(0);
    }

    public static String getLastLoadedAt() {
        return lastLoadedAt;
    }

    private static List<XpReferenceData> fetch(String endpoint) throws Exception {
        String baseUrl = normalizedBackendUrl();
        if (baseUrl.isBlank()) throw new IllegalStateException("apiUrl non configure");
        String response = ApiHttpClient.rawJson("GET", baseUrl + endpoint, null);
        return parse(response);
    }

    private static List<XpReferenceData> parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray values = asArray(root);
        List<XpReferenceData> result = new ArrayList<>();
        for (JsonElement raw : values) {
            if (!raw.isJsonObject()) continue;
            JsonObject value = raw.getAsJsonObject();
            Integer xpAmount = integer(value, "xpAmount", "xp_amount", "xp", "amount");
            if (xpAmount == null) continue;
            String itemId = string(value, "itemId", "item_id", "item");
            result.add(new XpReferenceData(
                    string(value, "action"),
                    string(value, "job", "jobId", "job_id", "metier"),
                    itemId,
                    firstNonBlank(string(value, "displayName", "display_name", "name"), itemId),
                    firstNonBlank(string(value, "category"), "AUTRE"),
                    xpAmount
            ));
        }
        return result;
    }

    private static JsonArray asArray(JsonElement root) {
        if (root == null || root.isJsonNull()) return new JsonArray();
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (!root.isJsonObject()) return new JsonArray();
        JsonObject object = root.getAsJsonObject();
        for (String key : List.of("results", "items", "references", "xpReferences", "xp_references", "data")) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray()) return value.getAsJsonArray();
        }
        return new JsonArray();
    }

    private static synchronized void saveCache() {
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Files.writeString(CACHE_PATH, GSON.toJson(new Cache(lastLoadedAt, references)), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MedievalCoin.LOGGER.warn("Cache references XP non sauvegarde: {}", exception.getMessage());
        }
    }

    private static synchronized void loadCache() {
        if (!references.isEmpty() || !Files.exists(CACHE_PATH)) return;
        try {
            Cache cache = GSON.fromJson(Files.readString(CACHE_PATH, StandardCharsets.UTF_8), Cache.class);
            if (cache != null && cache.items != null) {
                references = cache.items;
                lastLoadedAt = cache.loadedAt == null ? "cache" : cache.loadedAt;
            }
        } catch (Exception exception) {
            MedievalCoin.LOGGER.warn("Cache references XP non charge: {}", exception.getMessage());
        }
    }

    private static String normalizedBackendUrl() {
        String value = ModConfig.load().apiUrl == null ? "" : ModConfig.load().apiUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String string(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) return value.getAsString();
        }
        return null;
    }

    private static Integer integer(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) return value.getAsInt();
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static class Cache {
        String loadedAt;
        List<XpReferenceData> items;
        Cache(String loadedAt, List<XpReferenceData> items) {
            this.loadedAt = loadedAt;
            this.items = items;
        }
    }
}
