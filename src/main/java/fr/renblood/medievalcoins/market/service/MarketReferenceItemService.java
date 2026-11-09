package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.ReferenceItemData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MarketReferenceItemService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get().resolve("medieval_coins").resolve("market_reference_items_cache.json");
    private static volatile List<ReferenceItemData> backendItems = List.of();
    private static volatile String lastLoadedAt = "jamais";

    private MarketReferenceItemService() {
    }

    public static CompletableFuture<Boolean> reloadAsync() {
        return MarketApiService.fetchReferenceItems().handle((items, failure) -> {
            if (failure == null && items != null) {
                backendItems = items;
                lastLoadedAt = Instant.now().toString();
                saveCache();
                MedievalCoin.LOGGER.info("Items de reference marche charges: {}", items.size());
                return true;
            } else {
                loadCache();
                MedievalCoin.LOGGER.warn("Items de reference backend indisponibles, utilisation du cache des prix");
                return false;
            }
        });
    }

    public static List<ReferenceItemData> getBackendItems() {
        if (backendItems.isEmpty()) loadCache();
        return backendItems;
    }

    public static String getLastLoadedAt() {
        return lastLoadedAt;
    }

    private static synchronized void saveCache() {
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Files.writeString(CACHE_PATH, GSON.toJson(new Cache(lastLoadedAt, backendItems)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MedievalCoin.LOGGER.warn("Cache items de reference non sauvegarde: {}", e.getMessage());
        }
    }

    private static synchronized void loadCache() {
        if (!backendItems.isEmpty() || !Files.exists(CACHE_PATH)) return;
        try {
            Cache cache = GSON.fromJson(Files.readString(CACHE_PATH, StandardCharsets.UTF_8), Cache.class);
            if (cache != null && cache.items != null) {
                backendItems = cache.items;
                lastLoadedAt = cache.loadedAt;
            }
        } catch (Exception e) {
            MedievalCoin.LOGGER.warn("Cache items de reference non charge: {}", e.getMessage());
        }
    }

    private static class Cache {
        String loadedAt;
        List<ReferenceItemData> items;
        Cache(String loadedAt, List<ReferenceItemData> items) { this.loadedAt = loadedAt; this.items = items; }
    }
}
