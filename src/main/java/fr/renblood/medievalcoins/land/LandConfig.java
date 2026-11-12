package fr.renblood.medievalcoins.land;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LandConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("medievalcoins").resolve("land_config.json");
    private static boolean initialized;
    private static LandConfigData current = new LandConfigData();

    private LandConfig() {
    }

    public static synchronized LandConfigData get() {
        if(!initialized)reload();
        return current;
    }

    public static synchronized LandConfigData reload() {
        initialized=true;
        ensureFileExists();

        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            LandConfigData loaded = GSON.fromJson(reader, LandConfigData.class);
            current = loaded == null ? new LandConfigData() : loaded.sanitized();
            saveCurrent();
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Impossible de charger la configuration de cadastre depuis {}", FILE, e);
            current = new LandConfigData();
        }

        return current;
    }

    public static boolean canAdmin(CommandSourceStack source) {
        return source.hasPermission(get().getAdminPermissionLevel());
    }

    private static void ensureFileExists() {
        if (Files.exists(FILE)) {
            return;
        }

        try {
            Files.createDirectories(FILE.getParent());
            current = new LandConfigData();
            saveCurrent();
        } catch (IOException e) {
            MedievalCoin.LOGGER.error("Impossible de creer le fichier de configuration de cadastre {}", FILE, e);
        }
    }

    private static void saveCurrent() throws IOException {
        Files.createDirectories(FILE.getParent());
        try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(current, writer);
        }
    }

    public static class LandConfigData {
        private boolean enableLandSystem = true;
        private boolean webSyncEnabled = true;
        private boolean webSyncInSingleplayer = false;
        private int webSyncIntervalSeconds = 120;
        private String webWorldKey = "renblood";
        public boolean isWebSyncEnabled(){return webSyncEnabled;}
        public boolean isWebSyncInSingleplayer(){return webSyncInSingleplayer;}
        public int getWebSyncIntervalSeconds(){return webSyncIntervalSeconds;}
        public String getWebWorldKey(){return webWorldKey;}

        private boolean showActionBarOnEnter = true;
        private int adminPermissionLevel = 2;
        private int mapRadiusX = 4;
        private int mapRadiusZ = 2;

        public boolean isEnableLandSystem() {
            return enableLandSystem;
        }

        public boolean isShowActionBarOnEnter() {
            return showActionBarOnEnter;
        }

        public int getAdminPermissionLevel() {
            return adminPermissionLevel;
        }

        public int getMapRadiusX() {
            return mapRadiusX;
        }

        public int getMapRadiusZ() {
            return mapRadiusZ;
        }

        private LandConfigData sanitized() {
            webSyncIntervalSeconds=Math.max(30,Math.min(3600,webSyncIntervalSeconds));
            if(webWorldKey==null||!webWorldKey.matches("[a-z0-9_-]{1,64}"))webWorldKey="renblood";
            if (adminPermissionLevel < 0) {
                adminPermissionLevel = 0;
            } else if (adminPermissionLevel > 4) {
                adminPermissionLevel = 4;
            }
            if (mapRadiusX < 0) {
                mapRadiusX = 0;
            } else if (mapRadiusX > 4) {
                mapRadiusX = 4;
            }
            if (mapRadiusZ < 0) {
                mapRadiusZ = 0;
            } else if (mapRadiusZ > 2) {
                mapRadiusZ = 2;
            }
            return this;
        }
    }
}
