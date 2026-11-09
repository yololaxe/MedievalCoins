package fr.renblood.medievalcoins.bank;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.ApiExecutor;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class QuestRewardService {
    private QuestRewardService() {}
    private static String path(String quest,String suffix){return "quests/"+URLEncoder.encode(quest,StandardCharsets.UTF_8)+"/"+suffix;}
    public static void complete(ServerPlayer player,String quest)throws Exception {
        if(player.getServer().isSameThread())throw new IllegalStateException("Quest API must run on the bounded worker");
        CommerceProtocol.post(player.getStringUUID(),path(quest,"complete"),new JsonObject());
    }
    public static void grant(ServerPlayer player,String quest){
        MinecraftServer server=player.getServer();
        if(!server.isSameThread()){server.execute(()->grant(player,quest));return;}
        run(()->{
            String uuid=player.getStringUUID();UUID key=UUID.nameUUIDFromBytes((uuid+":quest:"+quest).getBytes(StandardCharsets.UTF_8));
            JsonObject entry=BankJournal.find(server,uuid,key.toString());
            if(entry==null){entry=new JsonObject();entry.addProperty("idempotency_key",key.toString());entry.addProperty("type","QUEST_REWARD");entry.addProperty("quest_id",quest);entry.addProperty("local_phase","REQUESTED");BankJournal.save(server,uuid,entry);}
            JsonObject pending=entry;
            server.execute(()->{
                if(!BankService.acquire(player)){player.sendSystemMessage(Component.literal("Récompense conservée ; remise à la prochaine connexion."));return;}
                EconomyFeedback.send(player,"PENDING","Récompense : remise en cours…",quest);
                run(()->{deliver(player,pending);return null;},player);
            });return null;
        },player,false);
    }
    private interface Work {Object run()throws Exception;}
    private static void run(Work work,ServerPlayer player){
        run(work,player,true);
    }
    private static void run(Work work,ServerPlayer player,boolean ownsLock){
        try{ApiExecutor.supply(()->{try{return work.run();}catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}}).whenComplete((unused,failure)->{
            if(failure!=null){player.getServer().execute(()->{if(ownsLock){BankService.release(player);EconomyFeedback.send(player,"REVIEW","Récompense conservée : remise à vérifier","");}player.sendSystemMessage(Component.literal("Récompense conservée dans le registre. Remise à vérifier ou à reprendre à la connexion."));});MedievalCoin.LOGGER.warn("Quest reward pending",failure);}
        });}catch(RuntimeException failure){if(ownsLock)BankService.release(player);}
    }
    public static void resume(ServerPlayer player){
        run(()->{
            var pending=BankJournal.pending(player.getServer(),player.getStringUUID());
            if(pending.stream().anyMatch(e->!e.get("type").getAsString().equals("QUEST_REWARD")))return null;
            String quest=pending.stream().filter(e->e.get("type").getAsString().equals("QUEST_REWARD")).findFirst().map(e->e.get("quest_id").getAsString()).orElse(null);
            if(quest==null){JsonObject remote=CommerceProtocol.get(player.getStringUUID(),"quests/pending");if(!remote.getAsJsonArray("quest_ids").isEmpty())quest=remote.getAsJsonArray("quest_ids").get(0).getAsString();}
            String selected=quest;
            if(selected!=null)player.getServer().execute(()->{if(!player.hasDisconnected())grant(player,selected);});return null;
        },player,false);
    }
    private static void completed(ServerPlayer player){EconomyFeedback.send(player,"SUCCESS","Récompense confirmée","");BankService.release(player);resume(player);}
    private static JsonObject material(String uuid,String quest,String state,String proof)throws Exception {
        JsonObject b=new JsonObject();b.addProperty("state",state);b.addProperty("proof_id",proof);
        return CommerceProtocol.post(uuid,path(quest,"materials"),b);
    }
    private static void deliver(ServerPlayer player,JsonObject entry)throws Exception {
        MinecraftServer server=player.getServer();String uuid=player.getStringUUID(),quest=entry.get("quest_id").getAsString(),proof=entry.get("idempotency_key").getAsString();
        if(BankJournal.pending(server,uuid).stream().anyMatch(e->!e.get("type").getAsString().equals("QUEST_REWARD")))throw new IllegalStateException("Une opération économique doit être rapprochée d’abord.");
        JsonObject reward=CommerceProtocol.post(uuid,path(quest,"reward"),new JsonObject());
        server.execute(()->BankService.refreshBalance(player));
        entry.add("receipt",reward);String local=entry.get("local_phase").getAsString(),state=reward.get("material_state").getAsString();
        if(state.equals("PENDING")&&reward.has("reviewed_by")&&reward.has("material_resolution")&&reward.get("material_resolution").getAsString().equals("RETRY")){
            local="REQUESTED";entry.addProperty("local_phase",local);BankJournal.save(server,uuid,entry);
        }
        if(state.equals("DELIVERED")){entry.addProperty("local_phase","DONE");BankJournal.save(server,uuid,entry);server.execute(()->completed(player));return;}
        if(local.equals("APPLIED")){
            material(uuid,quest,"DELIVERED",proof);entry.addProperty("local_phase","DONE");BankJournal.save(server,uuid,entry);server.execute(()->completed(player));return;
        }
        if(!local.equals("REQUESTED")||!state.equals("PENDING")){
            if(state.equals("DELIVERING"))material(uuid,quest,"RECONCILIATION_REQUIRED",proof);
            throw new IllegalStateException("La remise matérielle doit être rapprochée.");
        }
        // DELIVERING is reserved before scheduling any inventory/XP change.
        material(uuid,quest,"DELIVERING",proof);entry.addProperty("local_phase","APPLYING");BankJournal.save(server,uuid,entry);
        server.execute(()->{
            try{
                if(player.hasDisconnected())throw new IllegalStateException("Joueur déconnecté");
                boolean received=CommerceInventory.mutate(player,()->{
                    for(var value:reward.getAsJsonArray("rewards")){
                        JsonObject item=value.getAsJsonObject();ResourceLocation id=ResourceLocation.tryParse(item.get("itemId").getAsString());
                        var type=id==null?null:ForgeRegistries.ITEMS.getValue(id);int count=item.get("count").getAsInt();
                        if(type==null||count<=0)throw new IllegalStateException("Récompense matérielle invalide");
                        while(count>0){int n=Math.min(count,type.getMaxStackSize());if(!player.getInventory().add(new ItemStack(type,n)))return false;count-=n;}
                    }return true;
                });
                if(!received){
                    run(()->{material(uuid,quest,"PENDING",proof);entry.addProperty("local_phase","REQUESTED");BankJournal.save(server,uuid,entry);
                        server.execute(()->{BankService.release(player);EconomyFeedback.send(player,"ERROR","Objets conservés : libérez de la place",proof);player.sendSystemMessage(Component.literal("Libérez de la place. Vos objets restent disponibles à la prochaine connexion ; l’argent est en banque."));});return null;},player);
                    return;
                }
                BankPlayerSnapshot.save(player);
                run(()->{
                    JsonObject xp=reward.getAsJsonObject("xp");
                    if(xp!=null&&xp.has("amount")&&xp.get("amount").getAsInt()>0&&xp.has("job"))
                        ApiClient.manageJobXp(uuid,"add",xp.get("job").getAsString(),xp.get("amount").getAsInt());
                    entry.addProperty("local_phase","APPLIED");BankJournal.save(server,uuid,entry);
                    material(uuid,quest,"DELIVERED",proof);entry.addProperty("local_phase","DONE");BankJournal.save(server,uuid,entry);
                    server.execute(()->{completed(player);player.sendSystemMessage(Component.literal("Récompense reçue. "+reward.get("amount").getAsString()+" fer versés en banque."));});return null;
                },player);
            }catch(Exception failure){BankService.release(player);EconomyFeedback.send(player,"REVIEW","Remise à vérifier",proof);player.sendSystemMessage(Component.literal("Vos gains sont conservés. Remise des objets à vérifier : "+proof));MedievalCoin.LOGGER.warn("Quest material delivery ambiguous",failure);}
        });
    }
    /** Recovery owns the shared bank lock; delivery itself releases it. */
    public static void recover(ServerPlayer player,JsonObject entry)throws Exception {deliver(player,entry);}
}
