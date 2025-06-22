package fr.renblood.medievalcoins.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.config.ModConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class ApiClient {
    private static final Gson GSON = new Gson();

    /**
     * Récupère tous les joueurs depuis /players?rank=<rank>
     * @param "rank" "admin" pour tout voir, sinon "citoyen", etc.
     */
    /** Récupère un seul joueur via /players/get/<mcId>/ */
    public static PlayerModel getPlayer(String mcId) throws Exception {
        var cfg = ModConfig.load();
        if (cfg.apiKey == null || cfg.apiKey.isEmpty())
            throw new IllegalStateException("API key non configurée");

        String endpoint = cfg.apiUrl + "/players/getByMinecraft/" + mcId + "/";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300)
            throw new RuntimeException("HTTP " + code + " sur " + endpoint);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            return PlayerModel.fromJson(sb.toString());
        } finally {
            conn.disconnect();
        }
    }
    public static List<PlayerModel> getPlayers(String rank) throws Exception {
        ModConfig cfg = ModConfig.load();
        // Si pas de clé, on arrête tout de suite
        if (cfg.apiKey == null || cfg.apiKey.isEmpty()) {
            throw new IllegalStateException("API key non configurée (use /mcconfig set apikey <key>)");
        }

        String endpoint = cfg.apiUrl + "/players/getPlayers/" + rank + "/";
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        // On peut passer la clé dans un header
        conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
        conn.setConnectTimeout(3_000);
        conn.setReadTimeout(3_000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("Erreur HTTP " + code + " sur " + endpoint);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line);
        }
        in.close();
        conn.disconnect();

        // Désérialisation
        Type listType = new TypeToken<List<PlayerModel>>(){}.getType();
        return GSON.fromJson(sb.toString(), listType);
    }
    public static int deposit(String playerId, int amount) throws Exception {
        ModConfig cfg = ModConfig.load();
        if (cfg.apiKey == null || cfg.apiKey.isEmpty()) {
            throw new IllegalStateException("API key non configurée");
        }

        String endpoint = cfg.apiUrl + "/players/deposit/" + playerId + "/";
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3_000);
        conn.setReadTimeout(3_000);

        // Corps JSON : { "amount": 1234 }
        JsonObject body = new JsonObject();
        body.addProperty("amount", amount);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(GSON.toJson(body).getBytes());
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("Erreur HTTP " + code + " sur " + endpoint);
        }

        // Le serveur renvoie le nouveau solde brut dans un champs JSON "new_balance"
        JsonObject resp = GSON.fromJson(new java.io.InputStreamReader(conn.getInputStream()), JsonObject.class);
        conn.disconnect();

        return resp.get("new_balance").getAsInt();
    }
}
