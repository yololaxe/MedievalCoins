package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MerchantCounterRegistry {
    private static final Map<String, MerchantCounterBlockEntity> LOADED = new ConcurrentHashMap<>();

    private MerchantCounterRegistry() {
    }

    public static void register(MerchantCounterBlockEntity counter) {
        LOADED.put(counter.getCounterId(), counter);
        MerchantCounterLocalStore.remember(counter.toDto(), counter.isPendingSync(), counter.isPendingCreate());
    }

    public static void unregister(MerchantCounterBlockEntity counter) {
        LOADED.remove(counter.getCounterId(), counter);
    }

    public static void idChanged(String oldId, MerchantCounterBlockEntity counter) {
        LOADED.remove(oldId, counter);
        register(counter);
    }

    public static MerchantCounterBlockEntity findLoaded(String counterId) {
        return LOADED.get(counterId);
    }

    public static List<MerchantCounterBlockEntity> loaded() {
        return new ArrayList<>(LOADED.values());
    }

    public static int countActivePlayerMarkets(String ownerId) {
        return (int) LOADED.values().stream()
                .filter(MerchantCounterBlockEntity::isPlayerMarket)
                .filter(MerchantCounterBlockEntity::isActive)
                .filter(counter -> ownerId.equals(counter.getOwnerId()))
                .count();
    }
}
