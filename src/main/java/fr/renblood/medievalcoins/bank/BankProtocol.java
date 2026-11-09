package fr.renblood.medievalcoins.bank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiHttpClient;

public final class BankProtocol {
    private BankProtocol() {}
    private static String url(String suffix) {
        return ModConfig.load().apiUrl.replaceAll("/+$", "") + "/api/economy/v1/" + suffix;
    }
    public static JsonObject prepare(String minecraft, JsonObject body) throws Exception {
        return JsonParser.parseString(ApiHttpClient.minecraftPost(url("operations"), body, minecraft)).getAsJsonObject();
    }
    public static JsonObject transition(String minecraft, String id, String action, int revision, String proof) throws Exception {
        JsonObject body=new JsonObject();body.addProperty("revision",revision);body.addProperty("proof_id",proof);
        return JsonParser.parseString(ApiHttpClient.minecraftPost(url("operations/"+id+"/"+action), body, minecraft)).getAsJsonObject();
    }
    public static long balance(String minecraft) throws Exception {
        JsonObject data=JsonParser.parseString(ApiHttpClient.minecraftJson("GET",url("accounts"),null,minecraft)).getAsJsonObject();
        JsonObject account=data.getAsJsonArray("accounts").get(0).getAsJsonObject();
        return Math.addExact(account.get("available").getAsLong(),account.get("reserved").getAsLong());
    }
    public static JsonObject change(String minecraft, String key, long amount, boolean credit, String reason) throws Exception {
        if(amount<=0)throw new IllegalArgumentException("Montant strictement positif requis");
        JsonObject body=new JsonObject();body.addProperty("type",credit?"CREDIT":"DEBIT");body.addProperty("amount",Long.toString(amount));body.addProperty("idempotency_key",key);body.addProperty("reason",reason);
        return prepare(minecraft,body);
    }
}
