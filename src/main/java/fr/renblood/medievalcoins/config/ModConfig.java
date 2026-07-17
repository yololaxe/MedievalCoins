package fr.renblood.medievalcoins.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIRECTORY = FMLPaths.CONFIGDIR.get().resolve("medieval_coins");
    private static final Path CONFIG_PATH = CONFIG_DIRECTORY.resolve("server_config.json");
    private static final Path LEGACY_CONFIG_PATH = CONFIG_DIRECTORY.resolve("client_config.json");

    private static ModConfig instance;

    public String apiUrl = "http://127.0.0.1:8000";
    public String apiKey = "";
    public int apiConnectTimeoutMs = 4000;
    public int apiReadTimeoutMs = 6000;
    public int apiRetryCount = 2;
    public int apiCacheTtlSeconds = 30;
    public int apiSyncIntervalSeconds = 60;
    public String adminAuditEndpoint = "/minecraft/admin/audit/";

    public static ModConfig load() {
        if (instance != null) return instance;
        try {
            // Crée le dossier config/medieval_coins s'il n'existe pas
            Files.createDirectories(CONFIG_PATH.getParent());

            if (!Files.exists(CONFIG_PATH) && Files.exists(LEGACY_CONFIG_PATH)) {
                Files.copy(LEGACY_CONFIG_PATH, CONFIG_PATH);
                System.out.println("[MedievalCoins] Configuration API migree vers " + CONFIG_PATH);
            }
            File cfgFile = CONFIG_PATH.toFile();
            if (!cfgFile.exists()) {
                instance = new ModConfig();
                save(); // écrit un nouveau fichier
                System.out.println("[MedievalCoins] Création config vierge en " + CONFIG_PATH);
            } else {
                try (FileReader reader = new FileReader(cfgFile)) {
                    instance = GSON.fromJson(reader, ModConfig.class);
                }
                if (instance == null) instance = new ModConfig();
                instance.sanitize();
                System.out.println("[MedievalCoins] Chargée config existante depuis " + CONFIG_PATH);
            }
        } catch (IOException e) {
            e.printStackTrace();
            instance = new ModConfig();
        }
        return instance;
    }

    public static ModConfig reload() {
        instance = null;
        fr.renblood.medievalcoins.network.ApiCache.clear();
        return load();
    }

    public static void save() {
        try {
            // Assure encore une fois que le dossier existe
            Files.createDirectories(CONFIG_PATH.getParent());

            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(load(), writer);
            }
            System.out.println("[MedievalCoins] Config sauvegardée dans " + CONFIG_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sanitize() {
        apiConnectTimeoutMs = Math.max(500, Math.min(apiConnectTimeoutMs, 30_000));
        apiReadTimeoutMs = Math.max(500, Math.min(apiReadTimeoutMs, 60_000));
        apiRetryCount = Math.max(0, Math.min(apiRetryCount, 5));
        apiCacheTtlSeconds = Math.max(0, Math.min(apiCacheTtlSeconds, 3600));
        apiSyncIntervalSeconds = Math.max(15, Math.min(apiSyncIntervalSeconds, 3600));
        if (adminAuditEndpoint == null || adminAuditEndpoint.isBlank()) {
            adminAuditEndpoint = "/minecraft/admin/audit/";
        }
    }
}
