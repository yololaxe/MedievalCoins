package fr.renblood.medievalcoins.bank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Called only from the bounded API executor, never the render/tick loop. */
public final class BankJournal {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private BankJournal() {}
    private static Path folder(MinecraftServer server,String uuid) throws Exception {
        Path path=server.getWorldPath(LevelResource.ROOT).resolve("data/renblood-bank").resolve(java.util.UUID.fromString(uuid).toString());
        Files.createDirectories(path);return path;
    }
    public static void save(MinecraftServer server,String uuid,JsonObject entry) throws Exception {
        String key=java.util.UUID.fromString(entry.get("idempotency_key").getAsString()).toString();
        Path active=folder(server,uuid).resolve(key+".json");
        boolean completed=entry.get("local_phase").getAsString().equals("DONE")||entry.get("local_phase").getAsString().equals("CANCELLED");
        Path directory=completed?folder(server,uuid).resolve("completed"):folder(server,uuid);
        Files.createDirectories(directory);
        Path target=directory.resolve(key+".json"),temp=target.resolveSibling(key+".tmp");
        byte[] bytes=GSON.toJson(entry).getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        // If the filesystem cannot guarantee the replace, fail before touching coins.
        Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        if(completed)Files.deleteIfExists(active);
    }
    public static JsonObject find(MinecraftServer server,String uuid,String operation) throws Exception {
        Path file=folder(server,uuid).resolve(java.util.UUID.fromString(operation).toString()+".json");
        if(!Files.exists(file))file=folder(server,uuid).resolve("completed").resolve(java.util.UUID.fromString(operation).toString()+".json");
        return Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():null;
    }
    public static List<JsonObject> pending(MinecraftServer server,String uuid) throws Exception {
        List<JsonObject> result=new ArrayList<>();
        try(var paths=Files.list(folder(server,uuid))){
            for(Path path:paths.filter(p->p.toString().endsWith(".json")).toList()){
                JsonObject entry=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String phase=entry.get("local_phase").getAsString();
                if(!phase.equals("DONE")&&!phase.equals("CANCELLED"))result.add(entry);
            }
        }
        return result;
    }
}
