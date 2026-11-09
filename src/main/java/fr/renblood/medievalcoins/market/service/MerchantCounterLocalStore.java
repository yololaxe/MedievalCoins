package fr.renblood.medievalcoins.market.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.model.MerchantCounterDto;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class MerchantCounterLocalStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("medieval_coins").resolve("merchant_counters_local.json");
    private static Data data;
    private static boolean dirty;
    private static boolean scheduled;
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Renblood-CounterCache");
        thread.setDaemon(true);
        return thread;
    });

    private MerchantCounterLocalStore() {
    }

    public static synchronized void remember(MerchantCounterDto counter, boolean pendingSync, boolean pendingCreate) {
        Entry entry = new Entry();
        entry.counter = counter;
        entry.pendingSync = pendingSync;
        entry.pendingCreate = pendingCreate;
        load().counters.put(counter.counterId, entry);
        scheduleSave();
    }

    public static synchronized void markSynced(String counterId, MerchantCounterDto backendValue) {
        Data current = load();
        current.counters.remove(counterId);
        Entry entry = new Entry();
        entry.counter = backendValue;
        entry.pendingSync = false;
        entry.pendingCreate = false;
        current.counters.put(backendValue.counterId, entry);
        scheduleSave();
    }

    public static synchronized void markSyncedIfUnchanged(String counterId, MerchantCounterDto sent, MerchantCounterDto backend) {
        Entry current = load().counters.get(counterId);
        // Entries are replaced, never mutated; identity also detects edits while a chunk unloads.
        if (current != null && current.counter == sent) markSynced(counterId, backend);
    }

    public static synchronized void remove(String counterId) {
        load().counters.remove(counterId);
        scheduleSave();
    }

    public static synchronized List<Entry> entries() {
        return new ArrayList<>(load().counters.values());
    }

    public static synchronized int count() {
        return load().counters.size();
    }

    public static synchronized int pendingCount() {
        return (int) load().counters.values().stream().filter(entry -> entry.pendingSync).count();
    }

    private static Data load() {
        if (data != null) return data;
        try {
            if (Files.exists(PATH)) data = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Data.class);
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Cache local des comptoirs illisible: {}", PATH, e);
        }
        if (data == null || data.counters == null) data = new Data();
        return data;
    }

    private static void scheduleSave() {
        dirty = true;
        if (scheduled) return;
        scheduled = true;
        WRITER.schedule(MerchantCounterLocalStore::flush, 500, TimeUnit.MILLISECONDS);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            WRITER.submit(MerchantCounterLocalStore::flush).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            MedievalCoin.LOGGER.warn("Cache des comptoirs encore en cours d’écriture à l’arrêt", failure);
        }
    }

    private static void flush() {
        Data snapshot;
        synchronized (MerchantCounterLocalStore.class) {
            scheduled = false;
            if (!dirty) return;
            snapshot = new Data();
            snapshot.counters.putAll(data.counters);
            if (data.pendingTransactions != null) snapshot.pendingTransactions.addAll(data.pendingTransactions);
            dirty = false;
        }
        long started = System.nanoTime();
        try {
            Files.createDirectories(PATH.getParent());
            Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(snapshot), StandardCharsets.UTF_8);
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            synchronized (MerchantCounterLocalStore.class) {
                dirty = true;
            }
            MedievalCoin.LOGGER.error("Impossible d'ecrire le cache local des comptoirs: {}", PATH, e);
        } finally {
            fr.renblood.medievalcoins.bank.EconomyTimings.counterCache(started);
        }
    }

    public static class Data {
        public Map<String, Entry> counters = new LinkedHashMap<>();
        public List<Object> pendingTransactions = new ArrayList<>();
    }

    public static class Entry {
        public MerchantCounterDto counter;
        public boolean pendingSync;
        public boolean pendingCreate;
    }
}
