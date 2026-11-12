package fr.renblood.medievalcoins.land;

import com.google.gson.*;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiExecutor;
import fr.renblood.medievalcoins.network.ApiHttpClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

@Mod.EventBusSubscriber(modid=MedievalCoin.MODID)
public final class LandWebSync {
    private static MinecraftServer active;
    private static boolean inFlight;
    private static long nextSync,manualAfter;
    private static String publishedHash="";
    private static JsonObject result;
    private static int failures;
    private LandWebSync() {}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(active==event.getServer()){active=null;inFlight=false;nextSync=0;publishedHash="";result=null;failures=0;manualAfter=0;}}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=event.getServer();long time=System.nanoTime();
        if(time<nextSync||inFlight)return;
        nextSync=time+30_000_000_000L;
        var settings=LandConfig.get();
        if(!settings.isEnableLandSystem()||!settings.isWebSyncEnabled()||!server.isDedicatedServer()&&!settings.isWebSyncInSingleplayer())return;
        ModConfig config=ModConfig.load();if(config.apiKey==null||config.apiKey.isBlank())return;
        if(active!=server){active=server;publishedHash="";result=null;failures=0;}
        send(server,false);
    }
    public static int request(CommandSourceStack source) {
        MinecraftServer server=source.getServer();var config=LandConfig.get();
        if(!config.isWebSyncEnabled()||!server.isDedicatedServer()&&!config.isWebSyncInSingleplayer()) {
            source.sendFailure(Component.literal("La synchronisation web est désactivée pour ce monde. Consulte land_config.json."));return 0;
        }
        long time=System.nanoTime();if(inFlight||time<manualAfter){source.sendFailure(Component.literal("Une synchronisation est déjà en cours ou vient d’être demandée. Patiente quelques secondes."));return 0;}
        manualAfter=time+30_000_000_000L;
        if(active!=server){active=server;publishedHash="";result=null;failures=0;}
        send(server,true);source.sendSuccess(()->Component.literal("Synchronisation du cadastre demandée. Utilise ensuite Actualiser sur le site."),false);return 1;
    }
    private static void send(MinecraftServer server,boolean manual) {
        var settings=LandConfig.get();nextSync=System.nanoTime()+settings.getWebSyncIntervalSeconds()*1_000_000_000L;
        try {
            LandDiscoveryData knowledge=LandDiscoveryData.get(server);
            JsonObject snapshot=LandStorage.webSnapshot();String hash=snapshot.get("hash").getAsString();
            List<LandDiscoveryData.Batch> rows=knowledge.pending();JsonObject body=new JsonObject();
            body.addProperty("worldKey",settings.getWebWorldKey());body.addProperty("worldId",knowledge.worldId().toString());
            if(!hash.equals(publishedHash))body.add("snapshot",snapshot);
            JsonArray discoveries=new JsonArray();
            for(var batch:rows) {
                JsonObject row=new JsonObject();row.addProperty("minecraft",batch.key().player().toString());row.addProperty("dimension",batch.key().dimension());row.addProperty("z",batch.key().z());
                JsonArray spans=new JsonArray();for(var span:batch.spans()){JsonArray pair=new JsonArray();pair.add(span.start());pair.add(span.end());spans.add(pair);}row.add("spans",spans);discoveries.add(row);
            }
            body.add("discoveries",discoveries);JsonArray results=new JsonArray();JsonObject sentResult=result;if(sentResult!=null)results.add(sentResult);body.add("results",results);
            String payload=body.toString();if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2*1024*1024)throw new IllegalStateException("Cadastre web trop volumineux ; aucune donnée n’a été supprimée.");
            String endpoint=ModConfig.load().apiUrl.replaceAll("/+$","")+"/api/lands/v1/sync/";
            inFlight=true;
            ApiExecutor.run(()->{
                try {
                    JsonObject response=JsonParser.parseString(ApiHttpClient.rawJson("POST",endpoint,payload)).getAsJsonObject();
                    server.execute(()->{
                        if(active!=server)return;
                        inFlight=false;failures=0;publishedHash=hash;knowledge.acknowledge(rows);
                        if(result==sentResult)result=null;
                        if(response.has("command")&&!response.get("command").isJsonNull()) {
                            JsonObject command=response.getAsJsonObject("command");result=LandStorage.applyWeb(command);LandPlayerTracker.clear();
                            nextSync=System.nanoTime()+5_000_000_000L;
                        } else if(!knowledge.pending().isEmpty())nextSync=System.nanoTime()+5_000_000_000L;
                    });
                } catch(Exception exception) {
                    server.execute(()->{
                        if(active!=server)return;inFlight=false;failures++;publishedHash="";
                        nextSync=System.nanoTime()+Math.min(600L,(long)settings.getWebSyncIntervalSeconds()*(1L<<Math.min(3,failures)))*1_000_000_000L;
                        if(failures==1||failures%10==0)MedievalCoin.LOGGER.warn("Cadastre web indisponible ; découvertes conservées localement : {}",exception.getMessage());
                    });
                }
            });
        }catch(Exception exception){inFlight=false;MedievalCoin.LOGGER.warn("Cadastre web non envoyé : {}",exception.getMessage());}
    }
}
