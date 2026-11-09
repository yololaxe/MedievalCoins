package fr.renblood.medievalcoins.market.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MarketConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("medieval_coins")
            .resolve("market_config.json");
    private static MarketConfig instance;

    public int marketRequestTimeoutSeconds = 5;
    public boolean useMarketCacheOnFailure = true;
    public boolean playerMarketsEnabled = true;
    public int maxPlayerMarketsPerPlayer = 3;
    public int maxItemsPerPlayerMarket = 54;
    public long maxStackPriceInIron = 262144L;
    public long minItemPriceInIron = 1L;
    public boolean allowNBTItemsInPlayerMarkets = true;
    public boolean allowDamagedItemsInPlayerMarkets = true;
    public boolean allowEnchantedItemsInPlayerMarkets = true;
    public List<String> blockedItemIds = new ArrayList<>();
    public List<String> allowedItemIds = null;
    public boolean requireAdminApprovalForPlayerMarkets = false;
    public int inactiveMarketDaysBeforeWarning = 14;
    public int inactiveMarketDaysBeforeDisable = 30;

    private MarketConfig() {
    }

    public static synchronized MarketConfig load() {
        if (instance != null) return instance;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                instance = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), MarketConfig.class);
            }
            if (instance == null) {
                instance = new MarketConfig();
                save();
            }
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Impossible de charger la configuration du marche {}", CONFIG_PATH, e);
            instance = new MarketConfig();
        }
        instance.marketRequestTimeoutSeconds = Math.max(1, instance.marketRequestTimeoutSeconds);
        instance.maxPlayerMarketsPerPlayer = Math.max(1, instance.maxPlayerMarketsPerPlayer);
        instance.maxItemsPerPlayerMarket = Math.max(1, instance.maxItemsPerPlayerMarket);
        instance.minItemPriceInIron = Math.max(1L, instance.minItemPriceInIron);
        instance.maxStackPriceInIron = Math.max(instance.minItemPriceInIron, instance.maxStackPriceInIron);
        instance.inactiveMarketDaysBeforeWarning = Math.max(1, instance.inactiveMarketDaysBeforeWarning);
        instance.inactiveMarketDaysBeforeDisable = Math.max(instance.inactiveMarketDaysBeforeWarning, instance.inactiveMarketDaysBeforeDisable);
        if (instance.blockedItemIds == null) instance.blockedItemIds = new ArrayList<>();
        return instance;
    }

    public static synchronized MarketConfig reload() {
        instance = null;
        return load();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(load()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MedievalCoin.LOGGER.error("Impossible d'ecrire la configuration du marche {}", CONFIG_PATH, e);
        }
    }

    public String normalizedBackendUrl() {
        String value = ModConfig.load().apiUrl == null ? "" : ModConfig.load().apiUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public String apiKey() {
        String key = ModConfig.load().apiKey;
        return key == null ? "" : key;
    }
}
