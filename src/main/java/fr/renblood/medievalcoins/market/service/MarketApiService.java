package fr.renblood.medievalcoins.market.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.config.MarketConfig;
import fr.renblood.medievalcoins.market.model.MarketPriceCache;
import fr.renblood.medievalcoins.market.model.MarketPriceDto;
import fr.renblood.medievalcoins.market.model.MerchantCounterDto;
import fr.renblood.medievalcoins.market.model.MarketTransactionDto;
import fr.renblood.medievalcoins.market.counter.CounterItemConfigData;
import fr.renblood.medievalcoins.market.counter.ReferenceItemData;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MarketApiService {
    private static final Gson GSON = new Gson();
    private static final java.util.concurrent.Executor EXECUTOR =
            fr.renblood.medievalcoins.network.ApiExecutor.executor();
    private static final List<String> ENDPOINTS = List.of(
            "/minecraft/markets/prices/current/",
            "/markets/public-prices/"
    );

    private MarketApiService() {
    }

    public static CompletableFuture<MarketPriceCache> fetchCurrentPrices() {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastFailure = null;
            for (String endpoint : ENDPOINTS) {
                try {
                    String response = get(endpoint);
                    MarketPriceCache parsed = parse(response);
                    MedievalCoin.LOGGER.info("Prix du marche charges depuis {}: {} prix", endpoint, parsed.prices.size());
                    return parsed;
                } catch (Exception e) {
                    lastFailure = e;
                    MedievalCoin.LOGGER.warn("Endpoint marche indisponible {}: {}", endpoint, e.getMessage());
                }
            }
            throw new IllegalStateException("Aucun endpoint de prix du marche disponible", lastFailure);
        }, EXECUTOR);
    }

    public static CompletableFuture<List<MerchantCounterDto>> fetchCounters() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseCounters(request("GET", "/minecraft/markets/counters/", null));
            } catch (Exception e) {
                throw new IllegalStateException("Impossible de recuperer les comptoirs", e);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<MerchantCounterDto> createCounter(MerchantCounterDto counter) {
        return writeCounter("POST", "/minecraft/markets/counters/", counter);
    }

    public static CompletableFuture<MerchantCounterDto> updateCounter(MerchantCounterDto counter) {
        return writeCounter("PUT", "/minecraft/markets/counters/" + counter.counterId + "/", counter);
    }

    public static CompletableFuture<Boolean> isReachable() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                request("GET", "/minecraft/markets/counters/", null);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<Void> sendTransaction(MarketTransactionDto transaction) {
        return CompletableFuture.runAsync(() -> {
            try {
                request("POST", "/minecraft/markets/transactions/", transactionJson(transaction));
            } catch (Exception e) {
                throw new IllegalStateException("Envoi de transaction impossible", e);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<List<MarketTransactionDto>> fetchCounterTransactions(String counterId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseTransactions(request("GET", "/minecraft/markets/counters/" + encode(counterId) + "/transactions/", null));
            } catch (Exception e) {
                throw new IllegalStateException("Impossible de recuperer l'historique du comptoir", e);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<List<ReferenceItemData>> fetchReferenceItems() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseReferenceItems(request("GET", "/minecraft/markets/reference-items/", null));
            } catch (Exception e) {
                throw new IllegalStateException("Impossible de recuperer les items de reference", e);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<Boolean> upsertReferenceItem(String itemId, String displayName, String category,
                                                                  long referencePrice, long minPrice, long maxPrice) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject value = new JsonObject();
                value.addProperty("item_id", itemId);
                value.addProperty("display_name", displayName);
                value.addProperty("category", category);
                value.addProperty("reference_price", referencePrice);
                value.addProperty("min_price", minPrice);
                value.addProperty("max_price", maxPrice);
                value.addProperty("enabled", true);
                request("POST", "/minecraft/markets/reference-items/", GSON.toJson(value));
                return true;
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Synchronisation de la reference marche impossible pour {}", itemId, e);
                return false;
            }
        }, EXECUTOR).thenCompose(saved -> saved
                ? MarketReferenceItemService.reloadAsync()
                : CompletableFuture.completedFuture(false));
    }

    public static CompletableFuture<Void> upsertCounterItem(String counterId, CounterItemConfigData item) {
        return CompletableFuture.runAsync(() -> {
            try {
                String base = "/minecraft/markets/counters/" + encode(counterId) + "/items/";
                try {
                    request("PUT", base + encode(item.itemId()) + "/", GSON.toJson(item));
                } catch (Exception exception) {
                    if (!isMethodFallback(exception)) throw exception;
                    request("POST", base, GSON.toJson(item));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Synchronisation item comptoir impossible", e);
            }
        }, EXECUTOR);
    }

    private static boolean isMethodFallback(Exception exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("HTTP 404") || message.contains("HTTP 405"));
    }

    public static CompletableFuture<List<CounterItemConfigData>> fetchCounterItems(String counterId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parseCounterItems(request("GET", "/minecraft/markets/counters/" + encode(counterId) + "/items/", null));
            } catch (Exception e) {
                throw new IllegalStateException("Impossible de recuperer les items du comptoir", e);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<Void> deleteCounterItem(String counterId, String itemId) {
        return CompletableFuture.runAsync(() -> {
            try {
                request("DELETE", "/minecraft/markets/counters/" + encode(counterId) + "/items/" + encode(itemId) + "/", null);
            } catch (Exception e) {
                throw new IllegalStateException("Suppression item comptoir impossible", e);
            }
        }, EXECUTOR);
    }

    private static String transactionJson(MarketTransactionDto transaction) {
        JsonObject value = new JsonObject();
        value.addProperty("counterId", transaction.counterId);
        value.addProperty("playerUuid", transaction.playerUuid);
        value.addProperty("playerName", transaction.playerName);
        value.addProperty("itemId", transaction.itemId);
        value.addProperty("itemDisplayName", transaction.itemDisplayName);
        value.addProperty("action", transaction.action);
        value.addProperty("quantity", transaction.quantity);
        value.addProperty("unitPrice", transaction.unitPrice);
        value.addProperty("totalPrice", transaction.totalPrice);
        value.addProperty("ownerGain", transaction.ownerGain);
        return GSON.toJson(value);
    }

    private static List<ReferenceItemData> parseReferenceItems(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray values = root.isJsonArray() ? root.getAsJsonArray()
                : root.getAsJsonObject().has("results") ? root.getAsJsonObject().getAsJsonArray("results")
                : root.getAsJsonObject().getAsJsonArray("items");
        List<ReferenceItemData> result = new ArrayList<>();
        for (JsonElement raw : values) {
            JsonObject value = raw.getAsJsonObject();
            String itemId = string(value, "itemId", "item_id");
            if (itemId == null || itemId.isBlank()) continue;
            String displayName = string(value, "displayName", "display_name", "name");
            result.add(new ReferenceItemData(itemId, displayName == null || displayName.isBlank() ? itemId : displayName,
                    firstNonBlank(string(value, "category"), "AUTRE"), longValue(value, "referencePrice", "reference_price")));
        }
        return result;
    }

    private static List<CounterItemConfigData> parseCounterItems(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray values = root.isJsonArray() ? root.getAsJsonArray()
                : root.getAsJsonObject().has("results") ? root.getAsJsonObject().getAsJsonArray("results")
                : root.getAsJsonObject().getAsJsonArray("items");
        List<CounterItemConfigData> result = new ArrayList<>();
        for (JsonElement raw : values) {
            JsonObject value = raw.getAsJsonObject();
            String itemId = string(value, "itemId", "item_id");
            if (itemId == null || itemId.isBlank()) continue;
            String displayName = string(value, "displayName", "display_name");
            result.add(new CounterItemConfigData(itemId, displayName == null || displayName.isBlank() ? itemId : displayName,
                    bool(value, true, "buyEnabled", "buy_enabled"), bool(value, false, "sellEnabled", "sell_enabled"),
                    bool(value, true, "autoPrice", "auto_price"), longValue(value, "manualBuyPrice", "manual_buy_price"),
                    longValue(value, "manualSellPrice", "manual_sell_price"),
                    firstNonBlank(string(value, "stockMode", "stock_mode"), "UNLIMITED"),
                    integer(value, "stockQuantity", "stock_quantity"),
                    doubleValue(value, 1.0D, "localModifier", "local_modifier"),
                    bool(value, true, "active")));
        }
        return result;
    }

    private static List<MarketTransactionDto> parseTransactions(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray values = root.isJsonArray() ? root.getAsJsonArray()
                : root.getAsJsonObject().has("results") ? root.getAsJsonObject().getAsJsonArray("results")
                : root.getAsJsonObject().getAsJsonArray("transactions");
        List<MarketTransactionDto> result = new ArrayList<>();
        for (JsonElement raw : values) {
            if (!raw.isJsonObject()) continue;
            JsonObject value = raw.getAsJsonObject();
            MarketTransactionDto transaction = new MarketTransactionDto();
            transaction.id = string(value, "id");
            transaction.counterId = string(value, "counterId", "counter_id");
            transaction.playerUuid = string(value, "playerUuid", "player_uuid");
            transaction.playerName = string(value, "playerName", "player_name");
            transaction.itemId = string(value, "itemId", "item_id");
            transaction.itemDisplayName = string(value, "itemDisplayName", "item_display_name");
            transaction.action = firstNonBlank(string(value, "action"), "BUY");
            transaction.quantity = intValue(value, 0, "quantity");
            transaction.unitPrice = longValue(value, "unitPrice", "unit_price") == null ? 0L : longValue(value, "unitPrice", "unit_price");
            transaction.totalPrice = longValue(value, "totalPrice", "total_price") == null ? 0L : longValue(value, "totalPrice", "total_price");
            transaction.ownerGain = longValue(value, "ownerGain", "owner_gain") == null ? 0L : longValue(value, "ownerGain", "owner_gain");
            transaction.createdAt = string(value, "createdAt", "created_at");
            result.add(transaction);
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static CompletableFuture<MerchantCounterDto> writeCounter(String method, String endpoint, MerchantCounterDto counter) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = request(method, endpoint, counterJson(counter));
                if (response == null || response.isBlank()) return counter;
                JsonObject value = JsonParser.parseString(response).getAsJsonObject();
                if (value.has("counter") && value.get("counter").isJsonObject()) value = value.getAsJsonObject("counter");
                return parseCounter(value);
            } catch (Exception e) {
                throw new IllegalStateException("Synchronisation du comptoir impossible", e);
            }
        }, EXECUTOR);
    }

    static MarketPriceCache parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        MarketPriceCache result = new MarketPriceCache();
        if (root.isJsonArray()) {
            parseLegacyArray(root.getAsJsonArray(), result);
            return result;
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("Reponse JSON marche invalide");

        JsonObject object = root.getAsJsonObject();
        result.calculationVersion = string(object, "calculationVersion", "calculation_version");
        result.calculatedAt = string(object, "calculatedAt", "calculated_at");
        if (object.has("prices") && object.get("prices").isJsonArray()) {
            parseFullPrices(object.getAsJsonArray("prices"), null, null, result);
        } else if (object.has("cities") && object.get("cities").isJsonArray()) {
            for (JsonElement cityElement : object.getAsJsonArray("cities")) {
                if (!cityElement.isJsonObject()) continue;
                JsonObject city = cityElement.getAsJsonObject();
                String cityId = string(city, "cityId", "city_id", "id");
                String cityName = string(city, "cityName", "city_name", "name");
                if (city.has("prices") && city.get("prices").isJsonArray()) {
                    parseFullPrices(city.getAsJsonArray("prices"), cityId, cityName, result);
                } else {
                    result.prices.add(parseLegacyCity(city));
                }
            }
        } else {
            result.prices.add(parseLegacyCity(object));
        }
        applyMetadata(result);
        return result;
    }

    private static void parseFullPrices(JsonArray prices, String parentCityId, String parentCityName,
                                        MarketPriceCache result) {
        for (JsonElement element : prices) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            MarketPriceDto price = new MarketPriceDto();
            price.cityId = firstNonBlank(string(value, "cityId", "city_id", "city"), parentCityId);
            price.cityName = firstNonBlank(string(value, "cityName", "city_name"), parentCityName);
            price.itemId = string(value, "itemId", "item_id");
            price.displayName = string(value, "displayName", "display_name");
            price.marketType = string(value, "marketType", "market_type");
            price.category = string(value, "category");
            price.calculatedPrice = longValue(value, "calculatedPrice", "calculated_price");
            price.buyPrice = longValue(value, "buyPrice", "buy_price");
            price.sellPrice = longValue(value, "sellPrice", "sell_price");
            price.calculatedAt = string(value, "calculatedAt", "calculated_at");
            price.calculationVersion = string(value, "calculationVersion", "calculation_version");
            result.prices.add(price);
        }
    }

    private static void parseLegacyArray(JsonArray values, MarketPriceCache result) {
        for (JsonElement element : values) {
            if (element.isJsonObject()) result.prices.add(parseLegacyCity(element.getAsJsonObject()));
        }
        applyMetadata(result);
    }

    private static MarketPriceDto parseLegacyCity(JsonObject value) {
        MarketPriceDto price = new MarketPriceDto();
        price.cityId = string(value, "cityId", "city_id", "id");
        price.cityName = string(value, "cityName", "city_name", "name");
        price.bread = integer(value, "bread");
        price.iron = integer(value, "iron");
        price.wood = integer(value, "wood");
        price.itemId = string(value, "itemId", "item_id");
        price.displayName = string(value, "displayName", "display_name");
        price.marketType = string(value, "marketType", "market_type");
        price.category = string(value, "category");
        price.calculatedPrice = longValue(value, "calculatedPrice", "calculated_price");
        price.buyPrice = longValue(value, "buyPrice", "buy_price");
        price.sellPrice = longValue(value, "sellPrice", "sell_price");
        price.calculatedAt = string(value, "calculatedAt", "calculated_at");
        price.calculationVersion = string(value, "calculationVersion", "calculation_version");
        return price;
    }

    private static void applyMetadata(MarketPriceCache result) {
        for (MarketPriceDto price : result.prices) {
            if (price.calculationVersion == null) price.calculationVersion = result.calculationVersion;
            if (price.calculatedAt == null) price.calculatedAt = result.calculatedAt;
        }
    }

    private static String get(String endpoint) throws Exception {
        return request("GET", endpoint, null);
    }

    private static String request(String method, String endpoint, String body) throws Exception {
        MarketConfig config = MarketConfig.load();
        String baseUrl = config.normalizedBackendUrl();
        if (baseUrl.isBlank()) throw new IllegalStateException("apiUrl non configure");
        JsonObject payload = body == null ? null : JsonParser.parseString(body).getAsJsonObject();
        return fr.renblood.medievalcoins.network.ApiHttpClient.json(
                method, baseUrl + endpoint, payload, config.apiKey());
    }

    private static List<MerchantCounterDto> parseCounters(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray values;
        if (root.isJsonArray()) {
            values = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().has("results")) {
            values = root.getAsJsonObject().getAsJsonArray("results");
        } else if (root.isJsonObject() && root.getAsJsonObject().has("counters")) {
            values = root.getAsJsonObject().getAsJsonArray("counters");
        } else {
            throw new IllegalArgumentException("Liste de comptoirs invalide");
        }
        List<MerchantCounterDto> counters = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonObject()) counters.add(parseCounter(value.getAsJsonObject()));
        return counters;
    }

    private static MerchantCounterDto parseCounter(JsonObject value) {
        MerchantCounterDto counter = new MerchantCounterDto();
        counter.counterId = string(value, "counterId", "counter_id", "id");
        counter.name = string(value, "name");
        counter.type = string(value, "type", "counterType", "counter_type");
        counter.cityId = string(value, "cityId", "city_id", "city");
        counter.cityName = string(value, "cityName", "city_name");
        counter.ownerType = string(value, "ownerType", "owner_type");
        counter.ownerId = string(value, "ownerId", "owner_id");
        counter.ownerName = string(value, "ownerName", "owner_name");
        counter.active = bool(value, true, "active");
        counter.buyModeEnabled = bool(value, true, "buyModeEnabled", "buy_mode_enabled");
        counter.sellModeEnabled = bool(value, false, "sellModeEnabled", "sell_mode_enabled");
        counter.autoPrice = bool(value, true, "autoPrice", "auto_price");
        counter.dimension = string(value, "dimension");
        counter.x = integer(value, "x");
        counter.y = integer(value, "y");
        counter.z = integer(value, "z");
        counter.lastOwnerInteractionAt = longValue(value, "lastOwnerInteractionAt", "last_owner_interaction_at");
        counter.lastTransactionAt = longValue(value, "lastTransactionAt", "last_transaction_at");
        return counter;
    }

    private static String counterJson(MerchantCounterDto counter) {
        JsonObject value = (JsonObject) GSON.toJsonTree(counter);
        nullable(value, "cityId", counter.cityId);
        nullable(value, "cityName", counter.cityName);
        return GSON.toJson(value);
    }

    private static void nullable(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.add(key, null);
    }

    private static String summarize(String value) {
        String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 200 ? clean : clean.substring(0, 200) + "...";
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
            if (value != null && !value.isJsonNull()) return value.getAsInt();
        }
        return null;
    }

    private static int intValue(JsonObject object, int fallback, String... keys) {
        Integer value = integer(object, keys);
        return value == null ? fallback : value;
    }

    private static boolean bool(JsonObject object, boolean fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return value.getAsBoolean();
        }
        return fallback;
    }

    private static Long longValue(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return value.getAsLong();
        }
        return null;
    }

    private static double doubleValue(JsonObject object, double fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return value.getAsDouble();
        }
        return fallback;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
