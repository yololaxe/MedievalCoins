package fr.renblood.medievalcoins.land;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class LandRegistry {
    private static final Map<String, LandPlot> PLOTS_BY_ID = new LinkedHashMap<>();
    private static final Map<ChunkKey, LandPlot> PLOTS_BY_CHUNK = new HashMap<>();

    private LandRegistry() {
    }

    public static synchronized void replaceAll(Collection<LandPlot> plots) {
        PLOTS_BY_ID.clear();
        PLOTS_BY_CHUNK.clear();

        for (LandPlot plot : plots) {
            if (plot == null || plot.getId().isBlank()) {
                continue;
            }

            PLOTS_BY_ID.put(plot.getId(), plot);
            if (!plot.getDimension().isBlank()) {
                for (LandChunk chunk : plot.getChunks()) {
                    PLOTS_BY_CHUNK.put(new ChunkKey(plot.getDimension(), chunk.getX(), chunk.getZ()), plot);
                }
            }
        }
    }

    public static synchronized Optional<LandPlot> findAt(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return Optional.ofNullable(PLOTS_BY_CHUNK.get(new ChunkKey(dimension.location().toString(), chunkX, chunkZ)));
    }

    public static synchronized List<LandPlot> getPlots() {
        return Collections.unmodifiableList(new ArrayList<>(PLOTS_BY_ID.values()));
    }

    public static synchronized List<LandPlot> getPlotsByCity(String city) {
        String normalizedCity = normalize(city);
        return PLOTS_BY_ID.values().stream()
                .filter(plot -> normalize(plot.getCity()).equals(normalizedCity))
                .toList();
    }

    public static synchronized Set<String> getCities() {
        Set<String> cities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LandPlot plot : PLOTS_BY_ID.values()) {
            if (!plot.getCity().isBlank()) {
                cities.add(plot.getCity());
            }
        }
        return cities;
    }

    public static synchronized int size() {
        return PLOTS_BY_ID.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ChunkKey(String dimension, int x, int z) {
        private ChunkKey {
            dimension = normalize(dimension);
        }
    }
}
