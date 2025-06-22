package fr.renblood.medievalcoins.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE =
            new File("config/medieval_coins/client_config.json");

    public String apiUrl = "http://127.0.0.1:8000";
    public String apiKey = "";

    private static ModConfig instance;

    public static ModConfig load() {
        if (instance != null) return instance;
        try {
            if (!CONFIG_FILE.exists()) {
                instance = new ModConfig();
                save();
            } else {
                instance = GSON.fromJson(new FileReader(CONFIG_FILE), ModConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            instance = new ModConfig();
        }
        return instance;
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(load(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
