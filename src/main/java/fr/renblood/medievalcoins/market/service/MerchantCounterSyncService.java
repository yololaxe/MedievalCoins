package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.CounterItemConfigData;
import fr.renblood.medievalcoins.market.model.CounterSyncResult;
import fr.renblood.medievalcoins.market.model.MerchantCounterDto;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MerchantCounterSyncService {
    // All lane access happens on the owning server thread. Separate servers cannot share a lane.
    private static final Map<Key, CompletableFuture<Upload>> COUNTERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<Key> ITEMS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private MerchantCounterSyncService() { }

    public static void counterPlaced(MerchantCounterBlockEntity counter) {
        counter.markPending(true);
        if (server(counter) != null) upload(server(counter), counter.toDto(), true);
    }

    public static void counterModified(MerchantCounterBlockEntity counter) {
        counter.markPending(false);
        if (!counter.isPendingCreate() && server(counter) != null) upload(server(counter), counter.toDto(), false);
    }

    private static CompletableFuture<Upload> upload(MinecraftServer server, MerchantCounterDto sent, boolean created) {
        Key key = new Key(server, sent.counterId);
        if (COUNTERS.containsKey(key)) return COUNTERS.get(key);
        CompletableFuture<Upload> result = new CompletableFuture<>();
        COUNTERS.put(key, result);
        request(() -> created ? MarketApiService.createCounter(sent) : MarketApiService.updateCounter(sent))
                .whenComplete((backend, failure) -> server.execute(() -> {
                    try {
                        MerchantCounterBlockEntity loaded = MerchantCounterRegistry.findLoaded(sent.counterId);
                        if (loaded != null && server(loaded) != server) loaded = null;
                        if (failure == null) {
                            MerchantCounterDto value = normalize(backend, sent);
                            if (loaded != null) loaded.applyUploadSynced(value, sent, created);
                            else MerchantCounterLocalStore.markSyncedIfUnchanged(sent.counterId, sent, value);
                        } else {
                            MedievalCoin.LOGGER.warn("Comptoir {} conserve pendingSync: {}", sent.counterId, rootMessage(failure));
                        }
                        COUNTERS.remove(key);
                        // Coalesce edits made during this request into one following snapshot.
                        if (failure == null && loaded != null) {
                            if (loaded.isPendingSync()) upload(server, loaded.toDto(), loaded.isPendingCreate());
                            if (loaded.isItemsPendingSync()) itemsModified(loaded);
                        }
                        result.complete(new Upload(failure));
                    } catch (Throwable applyFailure) {
                        COUNTERS.remove(key);
                        result.complete(new Upload(applyFailure));
                    }
                }));
        return result;
    }

    public static void itemsModified(MerchantCounterBlockEntity counter) {
        MinecraftServer server = server(counter);
        if (server == null || counter.isPendingCreate() || counter.isPendingSync()) return;
        String id = counter.getCounterId();
        Key key = new Key(server, id);
        if (!ITEMS.add(key)) return;
        List<CounterItemConfigData> sent = counter.getConfiguredItems();
        Set<String> removed = counter.getRemovedItemIds();
        List<CompletableFuture<Void>> requests = new ArrayList<>();
        sent.forEach(item -> requests.add(request(() -> MarketApiService.upsertCounterItem(id, item))));
        removed.stream().filter(itemId -> sent.stream().noneMatch(item -> item.itemId().equals(itemId)))
                .forEach(itemId -> requests.add(request(() -> MarketApiService.deleteCounterItem(id, itemId))));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) ->
                server.execute(() -> {
                    ITEMS.remove(key);
                    MerchantCounterBlockEntity loaded = MerchantCounterRegistry.findLoaded(id);
                    if (loaded == null || server(loaded) != server) return;
                    if (failure == null) {
                        if (sent.equals(loaded.getConfiguredItems()) && removed.equals(loaded.getRemovedItemIds())) {
                            loaded.markItemsSynced();
                        } else if (loaded.isItemsPendingSync()) {
                            itemsModified(loaded);
                        }
                    } else {
                        MedievalCoin.LOGGER.warn("Items du comptoir {} conserves pendingSync: {}", id, rootMessage(failure));
                    }
                }));
    }

    public static CompletableFuture<CounterSyncResult> syncAll(MinecraftServer server) {
        return onServer(server, () -> MerchantCounterLocalStore.entries().stream()
                .filter(entry -> entry.pendingSync && entry.counter != null)
                .map(entry -> upload(server, entry.counter, entry.pendingCreate)).toList())
                .thenCompose(uploads -> CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new))
                        .thenCompose(ignored -> onServer(server, () -> {
                            Map<String, Snapshot> revisions = new HashMap<>();
                            MerchantCounterRegistry.loaded().stream().filter(counter -> server(counter) == server)
                                    .forEach(counter -> revisions.put(counter.getCounterId(), new Snapshot(counter, counter.getSyncRevision())));
                            return revisions;
                        })).thenCompose(revisions -> request(MarketApiService::fetchCounters)
                                .handle((counters, failure) -> new Preparation(
                                        uploads.stream().map(CompletableFuture::join).toList(),
                                        counters == null ? List.of() : counters, failure, revisions))))
                .thenCompose(preparation -> onServer(server, () -> apply(server, preparation)));
    }

    private static CounterSyncResult apply(MinecraftServer server, Preparation preparation) {
        MarketSyncService.recordBackendReachable(preparation.fetchFailure == null);
        int sent = 0, errors = 0, updated = 0, notFound = 0;
        List<String> messages = new ArrayList<>();
        for (Upload upload : preparation.uploads) {
            if (upload.failure != null) { errors++; messages.add(rootMessage(upload.failure)); }
            else sent++;
        }
        if (preparation.fetchFailure != null) { errors++; messages.add(rootMessage(preparation.fetchFailure)); }
        for (MerchantCounterDto backend : preparation.backendCounters) {
            if (backend.counterId == null) continue;
            MerchantCounterBlockEntity loaded = MerchantCounterRegistry.findLoaded(backend.counterId);
            if (loaded == null || server(loaded) != server) {
                notFound++;
            } else if (!loaded.isPendingSync() && preparation.revisions.containsKey(backend.counterId)
                    && preparation.revisions.get(backend.counterId).counter == loaded
                    && preparation.revisions.get(backend.counterId).revision == loaded.getSyncRevision()) {
                loaded.applyBackendValue(backend);
                updated++;
            }
        }
        for (MerchantCounterBlockEntity counter : MerchantCounterRegistry.loaded()) {
            if (server(counter) != server) continue;
            if (counter.isItemsPendingSync()) { itemsModified(counter); continue; }
            if (counter.isPendingCreate() || counter.isPendingSync()) continue;
            long revision = counter.getSyncRevision();
            request(() -> MarketApiService.fetchCounterItems(counter.getCounterId())).whenComplete((items, failure) ->
                    server.execute(() -> {
                        if (counter.isRemoved() || server(counter) != server) return;
                        if (failure == null && counter.getSyncRevision() == revision) counter.applyBackendItems(items);
                        else if (failure != null) MedievalCoin.LOGGER.warn("Items backend du comptoir {} non charges: {}",
                                counter.getCounterId(), rootMessage(failure));
                    }));
        }
        return new CounterSyncResult(preparation.backendCounters.size(), updated, notFound, sent, errors, messages);
    }

    private static MinecraftServer server(MerchantCounterBlockEntity counter) {
        return counter.getLevel() == null ? null : counter.getLevel().getServer();
    }

    private static <T> CompletableFuture<T> request(Supplier<CompletableFuture<T>> action) {
        try { return action.get(); }
        catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static <T> CompletableFuture<T> onServer(MinecraftServer server, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> {
            try { result.complete(action.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static MerchantCounterDto normalize(MerchantCounterDto backend, MerchantCounterDto fallback) {
        if (backend == null) return fallback;
        if (backend.counterId == null || backend.counterId.isBlank()) backend.counterId = fallback.counterId;
        if (backend.dimension == null) backend.dimension = fallback.dimension;
        if (backend.x == null) backend.x = fallback.x;
        if (backend.y == null) backend.y = fallback.y;
        if (backend.z == null) backend.z = fallback.z;
        return backend;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Snapshot(MerchantCounterBlockEntity counter, long revision) { }
    private record Key(MinecraftServer server, String id) { }
    private record Upload(Throwable failure) { }
    private record Preparation(List<Upload> uploads, List<MerchantCounterDto> backendCounters,
                               Throwable fetchFailure, Map<String, Snapshot> revisions) { }
}
