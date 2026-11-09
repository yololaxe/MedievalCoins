package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.service.XpReferenceService;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.market.service.MarketSyncService;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.ApiExecutor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class ApiCacheSyncService {
    private static long ticks;
    private static volatile boolean syncing;

    private ApiCacheSyncService() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || syncing) return;
        long interval = Math.max(1, ModConfig.load().apiSyncIntervalSeconds) * 20L;
        if (++ticks % interval != 0) return;
        syncing = true;
        try {
            ApiExecutor.supply(() -> {
                try {
                    ApiClient.getNpcs();
                    ApiClient.getNpcSpawns(null, true);
                    ApiClient.getAllQuests("");
                    return null;
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }).thenCompose(unused -> MarketSyncService.reloadPricesFromBackend())
                    .thenCompose(unused -> XpReferenceService.reloadAsync())
                    .whenComplete((unused, failure) -> {
                        syncing = false;
                        if (failure != null) {
                            MedievalCoin.LOGGER.warn("Synchronisation automatique des caches impossible: {}", failure.getMessage());
                        }
                    });
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            syncing = false;
        }
    }
}
