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
    // Résout le dossier config de Forge (~/.minecraft/config ou run/config)
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR
            .get()                            // <minecraft_root>/config
            .resolve("medieval_coins")       // <minecraft_root>/config/medieval_coins
            .resolve("client_config.json");  // .../medieval_coins/client_config.json

    private static ModConfig instance;

    public String apiUrl = "http://127.0.0.1:8000";
    public String apiKey = "";

    public static ModConfig load() {
        if (instance != null) return instance;
        try {
            // Crée le dossier config/medieval_coins s'il n'existe pas
            Files.createDirectories(CONFIG_PATH.getParent());

            File cfgFile = CONFIG_PATH.toFile();
            if (!cfgFile.exists()) {
                instance = new ModConfig();
                save(); // écrit un nouveau fichier
                System.out.println("[MedievalCoins] Création config vierge en " + CONFIG_PATH);
            } else {
                instance = GSON.fromJson(new FileReader(cfgFile), ModConfig.class);
                System.out.println("[MedievalCoins] Chargée config existante depuis " + CONFIG_PATH);
            }
        } catch (IOException e) {
            e.printStackTrace();
            instance = new ModConfig();
        }
        return instance;
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
}
