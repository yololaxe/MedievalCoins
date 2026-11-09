package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.config.MarketConfig;
import fr.renblood.medievalcoins.market.model.MarketPriceCache;
import fr.renblood.medievalcoins.market.model.MarketSyncResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class MarketSyncService {
    private static volatile MarketPriceCache currentPrices;
    private static volatile boolean usingCache;
    private static volatile String lastSyncStatus = "NOT_INITIALIZED";
    private static volatile Boolean backendReachable;
    private static CompletableFuture<MarketSyncResult> inFlight;

    private MarketSyncService() {
    }

    public static void initialize() {
        Optional<MarketPriceCache> cache = loadPricesFromCache();
        if (cache.isPresent()) {
            currentPrices = cache.get();
            usingCache = true;
            lastSyncStatus = "CACHE_LOADED_ON_STARTUP";
            MedievalCoin.LOGGER.info("Cache marche charge au demarrage: {} prix", currentPrices.prices.size());
        } else {
            lastSyncStatus = "NO_CACHE_ON_STARTUP";
        }
    }

    public static synchronized CompletableFuture<MarketSyncResult> reloadPricesFromBackend() {
        if (inFlight != null && !inFlight.isDone()) return inFlight;
        lastSyncStatus = "SYNC_IN_PROGRESS";
        MarketReferenceItemService.reloadAsync();
        inFlight = MarketApiService.fetchCurrentPrices().handle((prices, failure) -> {
            if (failure == null) {
                MarketCacheManager.save(prices);
                currentPrices = prices;
                usingCache = false;
                backendReachable = true;
                lastSyncStatus = "BACKEND_SUCCESS";
                return MarketSyncResult.backend(prices);
            }

            String reason = rootMessage(failure);
            MedievalCoin.LOGGER.error("Backend marche indisponible: {}", reason);
            backendReachable = false;
            if (MarketConfig.load().useMarketCacheOnFailure) {
                Optional<MarketPriceCache> cached = loadPricesFromCache();
                if (cached.isPresent()) {
                    currentPrices = cached.get();
                    usingCache = true;
                    lastSyncStatus = "CACHE_FALLBACK: " + reason;
                    return MarketSyncResult.cache(currentPrices, reason);
                }
            }
            usingCache = false;
            lastSyncStatus = "FAILED: " + reason;
            return MarketSyncResult.failure(reason);
        });
        return inFlight;
    }

    public static Optional<MarketPriceCache> loadPricesFromCache() {
        return MarketCacheManager.load();
    }

    public static Optional<MarketPriceCache> getCurrentPrices() {
        return Optional.ofNullable(currentPrices);
    }

    public static boolean isUsingCache() {
        return usingCache;
    }

    public static String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public static Boolean getBackendReachable() {
        return backendReachable;
    }

    public static void recordBackendReachable(boolean reachable) {
        backendReachable = reachable;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
