package fr.renblood.medievalcoins.land;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-world knowledge, stored as merged horizontal intervals, never loaded chunks. */
public final class LandDiscoveryData extends SavedData {
    public static final int RADIUS = 8, LIMIT = 1875000;
    public record Key(UUID player, String dimension, int z) {}
    public record Span(int start, int end) {}
    public record Batch(Key key, long revision, int offset, List<Span> spans) {}
    private static final class Row {
        List<Span> spans = List.of(); long revision; boolean pending; int offset;
    }
    private final Map<Key, Row> rows = new LinkedHashMap<>();
    private final UUID worldId;

    public LandDiscoveryData() { worldId = UUID.randomUUID(); setDirty(); }
    private LandDiscoveryData(UUID id) { worldId = id; }
    public UUID worldId() { return worldId; }
    public static LandDiscoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LandDiscoveryData::load, LandDiscoveryData::new, "renblood_land_discovery");
    }
    public boolean known(UUID player, String dimension, int x, int z) {
        Row row = rows.get(new Key(player, dimension, z));
        return row != null && row.spans.stream().anyMatch(s -> s.start <= x && x <= s.end);
    }
    public void visit(UUID player, String dimension, int x, int z) {
        if (Math.abs((long)x) > LIMIT || Math.abs((long)z) > LIMIT) return;
        for (int rowZ = Math.max(-LIMIT,z-RADIUS); rowZ <= Math.min(LIMIT,z+RADIUS); rowZ++) {
            Key key = new Key(player,dimension,rowZ);
            Row row = rows.computeIfAbsent(key,k -> new Row());
            List<Span> combined = union(row.spans, new Span(Math.max(-LIMIT,x-RADIUS),Math.min(LIMIT,x+RADIUS)));
            if (!combined.equals(row.spans)) { row.spans=combined; row.revision++; row.pending=true; row.offset=0; setDirty(); }
        }
    }
    public static List<Span> union(List<Span> current, Span added) {
        var sorted = new ArrayList<>(current); sorted.add(added); sorted.sort(Comparator.comparingInt(Span::start));
        var result = new ArrayList<Span>();
        for (Span span : sorted) {
            if (!result.isEmpty() && (long)span.start <= (long)result.get(result.size()-1).end+1) {
                Span old=result.remove(result.size()-1); result.add(new Span(old.start,Math.max(old.end,span.end)));
            } else result.add(span);
        }
        return List.copyOf(result);
    }
    public List<Batch> pending() {
        var result = new ArrayList<Batch>();
        for (var entry : rows.entrySet()) {
            Row row=entry.getValue();
            if (row.pending) result.add(new Batch(entry.getKey(),row.revision,row.offset,List.copyOf(row.spans.subList(row.offset,Math.min(row.offset+512,row.spans.size())))));
            if (result.size()==128) break;
        }
        return result;
    }
    public void acknowledge(List<Batch> batches) {
        for (var batch : batches) {
            Row row=rows.get(batch.key);
            if (row!=null && row.revision==batch.revision && row.offset==batch.offset) { row.offset+=batch.spans.size();if(row.offset>=row.spans.size()){row.pending=false;row.offset=0;}setDirty(); }
        }
    }
    public static LandDiscoveryData load(CompoundTag tag) {
        LandDiscoveryData data = new LandDiscoveryData(tag.hasUUID("world") ? tag.getUUID("world") : UUID.randomUUID());
        for (var item : tag.getList("rows",10)) {
            CompoundTag stored=(CompoundTag)item;
            if(!stored.hasUUID("player")) continue;
            Key key=new Key(stored.getUUID("player"),stored.getString("dimension"),stored.getInt("z"));
            Row row=new Row(); row.revision=stored.getLong("revision");row.pending=stored.getBoolean("pending");
            int[] intervals=stored.getIntArray("spans");var spans=new ArrayList<Span>();
            for(int i=0;i+1<intervals.length;i+=2) if(intervals[i]<=intervals[i+1])spans.add(new Span(intervals[i],intervals[i+1]));
            row.spans=List.copyOf(spans);data.rows.put(key,row);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putUUID("world",worldId);ListTag list=new ListTag();
        for(var entry:rows.entrySet()) {
            Key key=entry.getKey();Row row=entry.getValue();CompoundTag stored=new CompoundTag();
            stored.putUUID("player",key.player);stored.putString("dimension",key.dimension);stored.putInt("z",key.z);
            stored.putLong("revision",row.revision);stored.putBoolean("pending",row.pending);
            int[] intervals=new int[row.spans.size()*2];for(int i=0;i<row.spans.size();i++){intervals[i*2]=row.spans.get(i).start;intervals[i*2+1]=row.spans.get(i).end;}
            stored.putIntArray("spans",intervals);list.add(stored);
        }
        tag.put("rows",list);return tag;
    }
}
