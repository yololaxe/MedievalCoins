package fr.renblood.medievalcoins.network;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.config.ModConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiHttpClient {
    private ApiHttpClient() {
    }

    public static String get(String endpoint) throws Exception {
        return request("GET", endpoint, null, null, null, true);
    }

    public static String json(String method, String endpoint, JsonObject body) throws Exception {
        return json(method, endpoint, body, null);
    }

    public static String json(String method, String endpoint, JsonObject body, String apiKey) throws Exception {
        return request(method, endpoint, body == null ? null : body.toString(), null, apiKey, false);
    }

    public static String rawJson(String method, String endpoint, String body) throws Exception {
        return request(method, endpoint, body, null, null, "GET".equalsIgnoreCase(method));
    }

    public static String minecraftPost(String endpoint, JsonObject body, String minecraftUuid) throws Exception {
        return minecraftPost(endpoint, body, minecraftUuid, null);
    }

    public static String minecraftPost(String endpoint, JsonObject body, String minecraftUuid, String apiKey) throws Exception {
        if (minecraftUuid == null || minecraftUuid.isBlank()) {
            throw new IllegalArgumentException("UUID Minecraft obligatoire");
        }
        return request("POST", endpoint, body.toString(), minecraftUuid, apiKey, false);
    }

    public static String minecraftJson(String method, String endpoint, JsonObject body, String minecraftUuid) throws Exception {
        return request(method, endpoint, body == null ? null : body.toString(), minecraftUuid, null, false);
    }

    private static String request(String method, String endpoint, String body, String minecraftUuid,
                                  String apiKeyOverride, boolean retryable) throws Exception {
        long economyStarted=System.nanoTime();try{
        ModConfig config = ModConfig.load();
        String apiKey = resolveApiKey(apiKeyOverride, config.apiKey);
        requireApiKey(apiKey);
        int attempts = retryable ? Math.max(1, config.apiRetryCount + 1) : 1;
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return execute(method, endpoint, body, minecraftUuid, apiKey, config);
            } catch (TemporaryApiException exception) {
                lastFailure = exception;
                if (attempt == attempts) throw exception;
                Thread.sleep(Math.min(1_500L, 200L * (1L << (attempt - 1))));
            } catch (ApiException exception) {
                throw exception;
            } catch (IOException exception) {
                lastFailure = exception;
                if (attempt == attempts) throw exception;
                Thread.sleep(Math.min(1_500L, 200L * (1L << (attempt - 1))));
            }
        }
        throw lastFailure == null ? new IOException("Requete API impossible") : lastFailure;
    
        }finally{fr.renblood.medievalcoins.bank.EconomyTimings.api(economyStarted);}
}

    private static String execute(String method, String endpoint, String body, String minecraftUuid,
                                  String apiKey, ModConfig config) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod(method);
            setAuthenticationHeader(connection, endpoint, apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(config.apiConnectTimeoutMs);
            connection.setReadTimeout(config.apiReadTimeoutMs);

            if (minecraftUuid != null) {
                connection.setRequestProperty("X-Minecraft-UUID", minecraftUuid);
            }
            if (minecraftUuid != null && endpoint.endsWith("/players/onboarding/minecraft/confirm/")) {
                if (config.playerLinkServerId == null || config.playerLinkServerId.isBlank()
                        || config.playerLinkKey == null || config.playerLinkKey.isBlank()) {
                    throw new IllegalStateException("Le serveur doit être autorisé pour les associations Minecraft.");
                }
                connection.setRequestProperty("X-Renblood-Link-Server", config.playerLinkServerId);
                connection.setRequestProperty("X-Renblood-Link-Key", config.playerLinkKey);
            }
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = connection.getResponseCode();
            String response = read(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                if (code == 404 && new URL(endpoint).getPath().startsWith("/players/getByMinecraft/")) {
                    throw new ApiException("Aucun personnage du site n’est associé à cet UUID Minecraft. Associe ton compte Minecraft à ton personnage sur le site du même environnement.");
                }
                String message = "HTTP " + code + " sur " + endpoint + " : " + summarize(response);
                if (code == 429 || code >= 500) throw new TemporaryApiException(message);
                throw new ApiException(message);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private static String resolveApiKey(String apiKeyOverride, String configuredApiKey) {
        if (apiKeyOverride != null && !apiKeyOverride.isBlank()) return apiKeyOverride;
        return configuredApiKey;
    }

    private static void setAuthenticationHeader(HttpURLConnection connection, String endpoint, String apiKey) throws IOException {
        String normalizedApiKey = stripBearerPrefix(apiKey);
        String authorization = bearerValue(normalizedApiKey);
        if (isMinecraftEndpoint(endpoint)) {
            connection.setRequestProperty("X-API-KEY", normalizedApiKey);
            connection.setRequestProperty("Authorization", authorization);
            return;
        }
        connection.setRequestProperty("Authorization", authorization);
    }

    private static String stripBearerPrefix(String apiKey) {
        return apiKey.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? apiKey.substring("Bearer ".length()).trim()
                : apiKey;
    }

    private static String bearerValue(String apiKey) {
        return apiKey.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? apiKey
                : "Bearer " + apiKey;
    }

    private static boolean isMinecraftEndpoint(String endpoint) throws IOException {
        String path = new URL(endpoint).getPath();
        return path != null && path.startsWith("/minecraft/");
    }

    private static void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("API key non configuree");
    }

    private static String summarize(String response) {
        if (response == null || response.isBlank()) return "aucun detail";
        try {
            var json = com.google.gson.JsonParser.parseString(response);
            if (json.isJsonObject()) for (String key : java.util.List.of("message", "error", "detail")) {
                var value = json.getAsJsonObject().get(key);
                if (value != null && value.isJsonPrimitive()) {
                    String decoded = value.getAsString();
                    return decoded.length() <= 240 ? decoded : decoded.substring(0, 240) + "…";
                }
            }
        } catch (RuntimeException notJson) { /* Keep the bounded HTML/text fallback. */ }
        String summary = response.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return summary.length() <= 240 ? summary : summary.substring(0, 240) + "...";
    }

    public static class ApiException extends IOException {
        public ApiException(String message) {
            super(message);
        }
    }

    private static final class TemporaryApiException extends ApiException {
        private TemporaryApiException(String message) {
            super(message);
        }
    }
}
