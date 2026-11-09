package fr.renblood.medievalcoins.land;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LandStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("medievalcoins").resolve("lands.json");

    private LandStorage() {
    }

    public static synchronized List<LandPlot> load() {
        return readFile().plots;
    }

    public static synchronized void save(List<LandPlot> plots) throws IOException {
        LandFile data = readFile();
        data.plots = plots == null ? new ArrayList<>() : new ArrayList<>(plots);
        writeFile(data);
        LandRegistry.replaceAll(data.plots);
    }

    public static synchronized int saveAndReload(List<LandPlot> plots) throws IOException {
        save(plots);
        return LandRegistry.size();
    }

    public static synchronized int reloadRegistry() {
        List<LandPlot> plots = load();
        LandRegistry.replaceAll(plots);
        return LandRegistry.size();
    }

    public static Path getFile() {
        return FILE;
    }

    private static LandFile readFile() {
        ensureFileExists();

        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            LandFile data = GSON.fromJson(reader, LandFile.class);
            if (data == null) {
                return new LandFile();
            }
            if (data.plots == null) {
                data.plots = new ArrayList<>();
            }
            return data;
        } catch (Exception e) {
            MedievalCoin.LOGGER.error("Impossible de charger les parcelles depuis {}", FILE, e);
            return new LandFile();
        }
    }

    private static void writeFile(LandFile data) throws IOException {
        Files.createDirectories(FILE.getParent());
        try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
    }

    private static void ensureFileExists() {
        if (Files.exists(FILE)) {
            return;
        }

        try {
            Files.createDirectories(FILE.getParent());
            writeFile(new LandFile());
        } catch (IOException e) {
            MedievalCoin.LOGGER.error("Impossible de creer le fichier de parcelles {}", FILE, e);
        }
    }

    private static class LandFile {
        private int version = 1;
        private List<LandPlot> plots = new ArrayList<>();
    }
}
