package fr.renblood.medievalcoins.bank;

import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonParser;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiHttpClient;

public final class CommerceProtocol {
    private CommerceProtocol() {}
    private static final java.util.concurrent.ConcurrentHashMap<String,Long> CATALOG=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean fresh(String key){return System.currentTimeMillis()-CATALOG.getOrDefault(key,0L)<5000;}
    private static void remember(String key){if(CATALOG.size()>256)CATALOG.clear();CATALOG.put(key,System.currentTimeMillis());}
    public static JsonObject get(String minecraft,String path)throws Exception {
        String base=ModConfig.load().apiUrl.replaceAll("/+$","")+"/api/economy/v1/";
        return JsonParser.parseString(ApiHttpClient.minecraftJson("GET",base+path,null,minecraft)).getAsJsonObject();
    }
    public static JsonObject findOffer(String minecraft, String business, String offer) throws Exception {
        var response = get(minecraft, offerPath(business) + "&offer_id=" + encode(offer));
        for (var row : response.getAsJsonArray("offers")) {
            JsonObject item = row.getAsJsonObject();
            if (offer.equals(item.get("offer_id").getAsString())) return item;
        }
        return null;
    }

    public static StockOffers stockOffers(String minecraft, String business, boolean playerMarket) throws Exception {
        String scope = playerMarket ? "slots" : "limited";
        String path = offerPath(business) + "&scope=" + scope;
        List<JsonObject> rows = new ArrayList<>();
        String after = null;
        for (int page = 0; page < 64; page++) {
            JsonObject response = get(minecraft, after == null ? path : path + "&after=" + encode(after));
            boolean scoped = response.has("scope") && scope.equals(response.get("scope").getAsString());
            for (var row : response.getAsJsonArray("offers")) rows.add(row.getAsJsonObject());
            var cursor = response.get("next_cursor");
            if (!scoped || cursor == null || cursor.isJsonNull()) return new StockOffers(List.copyOf(rows), scoped);
            String next = cursor.getAsString();
            if (after != null && next.compareTo(after) <= 0) throw new IllegalStateException("Curseur d’offres invalide");
            after = next;
        }
        throw new IllegalStateException("Trop de pages d’offres pour un seul comptoir");
    }

    private static String offerPath(String business) {
        return "commerce/offers?business_id=" + encode(business);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record StockOffers(List<JsonObject> rows, boolean complete) {}

    public static JsonObject post(String minecraft,String path,JsonObject body) throws Exception {
        String base=ModConfig.load().apiUrl.replaceAll("/+$","")+"/api/economy/v1/";
        return JsonParser.parseString(ApiHttpClient.minecraftPost(base+path,body,minecraft)).getAsJsonObject();
    }
    public static void register(String minecraft,String business,String owner,String label,String kind) throws Exception {
        String cacheKey="business:"+business+":"+owner+":"+kind+":"+label;if(fresh(cacheKey))return;
        JsonObject body=new JsonObject();body.addProperty("business_id",business);body.addProperty("owner_minecraft",owner);
        body.addProperty("label",label);body.addProperty("kind",kind);post(minecraft,"commerce/businesses",body);remember(cacheKey);
    }
    public static void offer(String minecraft,String business,String id,String version,long buy,long sell,Integer stock,String nbt) throws Exception {
        offer(minecraft, business, id, version, buy, sell, stock, nbt, null);
    }
    public static void offer(String minecraft,String business,String id,String version,long buy,long sell,Integer stock,String nbt,Integer capacity) throws Exception {
        String cacheKey="offer:"+business+":"+id+":"+version+":"+nbt+":"+(stock==null)+":"+capacity;if(fresh(cacheKey))return;
        JsonObject body=new JsonObject();body.addProperty("business_id",business);body.addProperty("offer_id",id);
        body.addProperty("price_version",version);body.addProperty("buy_price",Long.toString(buy));body.addProperty("sell_price",Long.toString(sell));
        body.addProperty("stock",stock);body.addProperty("item_nbt",nbt);
        if (capacity != null) body.addProperty("stock_capacity", capacity);
        post(minecraft,"commerce/offers",body);remember(cacheKey);
    }
    public static JsonObject request(String business,String offer,String version,String action,long unit,int quantity,String payment) {
        JsonObject body=new JsonObject();body.addProperty("business_id",business);body.addProperty("offer_id",offer);
        body.addProperty("price_version",version);body.addProperty("action",action);body.addProperty("unit_price",Long.toString(unit));
        body.addProperty("quantity",quantity);body.addProperty("payment",payment);return body;
    }
}
