package fr.renblood.medievalcoins.land;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LandPlot {
    private String id = "";
    private String name = "";
    private String city = "";
    private String dimension = "";
    private String owner = "";
    private String ownerType = LandOwnerType.UNKNOWN.name();
    private String color = "";
    private String description = "";
    private List<LandChunk> chunks = new ArrayList<>();

    public LandPlot() {
    }

    public LandPlot(String id, String name) {
        this.id = clean(id);
        this.name = clean(name);
    }

    public String getId() {
        return clean(id);
    }

    public String getName() {
        return clean(name);
    }

    public String getCity() {
        return clean(city);
    }

    public String getDimension() {
        return clean(dimension);
    }

    public String getOwner() {
        return clean(owner);
    }

    public LandOwnerType getOwnerType() {
        return LandOwnerType.fromJson(ownerType);
    }

    public String getColor() {
        return clean(color);
    }

    public String getDescription() {
        return clean(description);
    }

    public List<LandChunk> getChunks() {
        if (chunks == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(chunks);
    }

    public void setName(String name) { this.name = clean(name); }

    public void setCity(String city) {
        this.city = clean(city);
    }

    public void setDimension(String dimension) {
        this.dimension = clean(dimension);
    }

    public void setOwner(LandOwnerType ownerType, String owner) {
        this.ownerType = ownerType.name();
        this.owner = clean(owner);
    }

    public void setColor(String color) {
        this.color = clean(color);
    }

    public void setDescription(String description) {
        this.description = clean(description);
    }

    public boolean hasChunk(int x, int z) {
        return mutableChunks().contains(new LandChunk(x, z));
    }

    public void addChunk(int x, int z) {
        LandChunk chunk = new LandChunk(x, z);
        if (!mutableChunks().contains(chunk)) {
            mutableChunks().add(chunk);
        }
    }

    public boolean removeChunk(int x, int z) {
        return mutableChunks().remove(new LandChunk(x, z));
    }

    public boolean isValid() {
        return !getId().isBlank() && !getDimension().isBlank() && !getChunks().isEmpty();
    }

    public String getDisplayName() {
        String plotName = getName();
        return plotName.isBlank() ? getId() : plotName;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private List<LandChunk> mutableChunks() {
        if (chunks == null) {
            chunks = new ArrayList<>();
        }
        return chunks;
    }
}
