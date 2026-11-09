package fr.renblood.medievalcoins.market.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.model.MarketPriceCache;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

public final class MarketCacheManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("medieval_coins")
            .resolve("market_prices_cache.json");

    private MarketCacheManager() {
    }

    public static synchronized Optional<MarketPriceCache> load() {
        if (!Files.exists(CACHE_PATH)) return Optional.empty();
        try {
            MarketPriceCache cache = GSON.fromJson(Files.readString(CACHE_PATH, StandardCharsets.UTF_8), MarketPriceCache.class);
            if (cache == null || cache.prices == null) return Optional.empty();
            return Optional.of(cache);
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Cache marche illisible: {}", CACHE_PATH, e);
            return Optional.empty();
        }
    }

    public static synchronized boolean save(MarketPriceCache cache) {
        if (cache == null) return false;
        cache.cachedAt = Instant.now().toString();
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Path temporary = CACHE_PATH.resolveSibling(CACHE_PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(cache), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, CACHE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, CACHE_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            MedievalCoin.LOGGER.error("Impossible d'ecrire le cache marche: {}", CACHE_PATH, e);
            return false;
        }
    }

    public static boolean exists() {
        return Files.exists(CACHE_PATH);
    }

    public static Path getCachePath() {
        return CACHE_PATH;
    }
}
