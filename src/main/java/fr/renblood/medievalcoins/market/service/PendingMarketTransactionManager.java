package fr.renblood.medievalcoins.market.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.model.MarketTransactionDto;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class PendingMarketTransactionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("medieval_coins").resolve("market_pending_transactions.json");
    private static List<MarketTransactionDto> pending;

    private PendingMarketTransactionManager() {
    }

    public static synchronized void add(MarketTransactionDto transaction) {
        if (load().stream().noneMatch(value -> value.localTransactionId.equals(transaction.localTransactionId))) {
            load().add(transaction);
            save();
        }
    }

    public static synchronized void remove(String localId) {
        load().removeIf(value -> value.localTransactionId.equals(localId));
        save();
    }

    public static synchronized List<MarketTransactionDto> entries() {
        return new ArrayList<>(load());
    }

    public static synchronized int count() {
        return load().size();
    }

    public static synchronized List<MarketTransactionDto> entriesForCounter(String counterId) {
        return load().stream().filter(value -> counterId.equals(value.counterId)).toList();
    }

    private static List<MarketTransactionDto> load() {
        if (pending != null) return pending;
        try {
            if (Files.exists(PATH)) {
                pending = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
                        new TypeToken<List<MarketTransactionDto>>() {}.getType());
            }
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Transactions marche pending illisibles: {}", PATH, e);
        }
        if (pending == null) pending = new ArrayList<>();
        return pending;
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(pending), StandardCharsets.UTF_8);
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Impossible d'ecrire les transactions pending: {}", PATH, e);
        }
    }
}
