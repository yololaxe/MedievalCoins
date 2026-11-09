package fr.renblood.medievalcoins.bank;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.*;
import fr.renblood.medievalcoins.inventory.banker.DepositGuiMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

@Mod.EventBusSubscriber(modid=MedievalCoin.MODID)
public final class BankService {
    private static final Set<UUID> BUSY=ConcurrentHashMap.newKeySet();
    private static final Map<UUID,Long> LAST_REQUEST=new ConcurrentHashMap<>();
    private BankService() {}
    public static boolean acquire(ServerPlayer player){return BUSY.add(player.getUUID());}
    public static void release(ServerPlayer player){BUSY.remove(player.getUUID());}
    public static void refreshBalance(ServerPlayer player){
        try{ApiExecutor.supply(()->{
            try{long balance=BankProtocol.balance(player.getStringUUID());player.getServer().execute(()->refresh(player,balance));}
            catch(Exception ignored){}return null;
        });}catch(RuntimeException ignored){}
    }
    private static Item[] coins(){return new Item[]{Coins.IRON_COIN.get(),Coins.BRONZE_COIN.get(),Coins.SILVER_COIN.get(),Coins.GOLD_COIN.get()};}
    private static long value(Item item){Item[] c=coins();for(int i=0;i<4;i++)if(item==c[i])return 1L<<(i*6);return 0;}
    private static void say(ServerPlayer player,String message){player.sendSystemMessage(Component.literal(message));}
    private static void refresh(ServerPlayer player,long balance){
        if(player.hasDisconnected() || player.getServer().getPlayerList().getPlayer(player.getUUID())!=player)return;
        ApiCache.invalidatePrefix("player:");
        var model=PlayerCache.getPlayer(player.getStringUUID());
        if(model!=null){model.money=balance;PlayerCache.updatePlayer(model);}
        MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(()->player),new MoneyUpdateMessage(player.getStringUUID(),balance));
    }
    public static void deposit(ServerPlayer player,boolean all,UUID operationId){
        if(!(player.containerMenu instanceof DepositGuiMenu menu))return;
        ItemStack[] menuStacks=new ItemStack[4];long total=0;
        for(int i=0;i<4;i++){
            menuStacks[i]=menu.getDepositInv().getItem(i).copy();
            if(!menuStacks[i].isEmpty() && menuStacks[i].getItem()!=coins()[i]){say(player,"Pièce de dépôt invalide.");return;}
            total=Math.addExact(total,Math.multiplyExact(value(menuStacks[i].getItem()),menuStacks[i].getCount()));
        }
        Map<Integer,ItemStack> inventoryStacks=new HashMap<>();
        if(all)for(int i=0;i<player.getInventory().items.size();i++){
            ItemStack stack=player.getInventory().items.get(i);
            if(value(stack.getItem())>0){inventoryStacks.put(i,stack.copy());total=Math.addExact(total,Math.multiplyExact(value(stack.getItem()),stack.getCount()));}
        }
        if(total<=0){say(player,"Aucune pièce à déposer.");return;}
        try { BankPlayerSnapshot.save(player); } catch (Exception e) { say(player,"Sauvegarde bancaire indisponible : aucune opération envoyée."); return; }
        start(player,operationId,"DEPOSIT",total,()->{
            if(player.hasDisconnected() || player.getServer().getPlayerList().getPlayer(player.getUUID())!=player || player.containerMenu!=menu)return false;
            for(int i=0;i<4;i++)if(!ItemStack.matches(menuStacks[i],menu.getDepositInv().getItem(i)))return false;
            for(var entry:inventoryStacks.entrySet())if(!ItemStack.matches(entry.getValue(),player.getInventory().items.get(entry.getKey())))return false;
            for(int i=0;i<4;i++)menu.getDepositInv().setItem(i,ItemStack.EMPTY);
            for(int slot:inventoryStacks.keySet())player.getInventory().items.set(slot,ItemStack.EMPTY);
            player.getInventory().setChanged();return true;
        });
    }
    public static void withdraw(ServerPlayer player,int type,int count,UUID operationId){
        if(type<0||type>3||count<1||count>64)return;
        ItemStack give=new ItemStack(coins()[type],count);
        start(player,operationId,"WITHDRAW",Math.multiplyExact(value(give.getItem()),count),()->{
            if(player.hasDisconnected() || player.getServer().getPlayerList().getPlayer(player.getUUID())!=player || !canFit(player,give))return false;
            return player.getInventory().add(give.copy());
        });
    }
    private static boolean canFit(ServerPlayer player,ItemStack stack){
        int needed=stack.getCount();
        for(ItemStack existing:player.getInventory().items){
            if(existing.isEmpty())needed-=stack.getMaxStackSize();
            else if(ItemStack.isSameItemSameTags(existing,stack))needed-=existing.getMaxStackSize()-existing.getCount();
            if(needed<=0)return true;
        }
        return false;
    }
    private static void start(ServerPlayer player,UUID operationId,String kind,long amount,BooleanSupplier apply){
        long now=System.currentTimeMillis();
        if(now-LAST_REQUEST.getOrDefault(player.getUUID(),0L)<1000){say(player,"Une demande vient d’être envoyée. Attendez son résultat.");return;}
        if(!BUSY.add(player.getUUID())){say(player,"Une opération bancaire est déjà en cours.");return;}
        LAST_REQUEST.put(player.getUUID(),now);
        EconomyFeedback.send(player,"PENDING","Vérification bancaire en cours…",operationId.toString());
        String uuid=player.getStringUUID();MinecraftServer server=player.getServer();
        JsonObject entry=new JsonObject();entry.addProperty("type",kind);entry.addProperty("amount",Long.toString(amount));entry.addProperty("idempotency_key",operationId.toString());entry.addProperty("local_phase","REQUESTED");
        say(player,"Banque : vérification en cours…");
        background(()->{
            JsonObject previous=BankJournal.find(server,uuid,operationId.toString());
            if(previous!=null){
                if(!previous.get("type").getAsString().equals(kind)||!previous.get("amount").getAsString().equals(Long.toString(amount)))throw new IllegalArgumentException("Clé bancaire déjà utilisée pour une autre demande");
                server.execute(()->{BUSY.remove(player.getUUID());say(player,"Cette demande a déjà été traitée : "+operationId);});return null;
            }
            if(BankJournal.pending(server,uuid).stream().anyMatch(e->!e.get("type").getAsString().equals("QUEST_REWARD")||!e.get("local_phase").getAsString().equals("REQUESTED")))throw new IllegalStateException("Un reçu bancaire doit être repris avant une nouvelle opération.");
            BankJournal.save(server,uuid,entry);
            JsonObject remote;
            try{remote=BankProtocol.prepare(uuid,entry);}
            catch(Exception failure){
                if(definiteRejection(failure)){entry.addProperty("local_phase","CANCELLED");BankJournal.save(server,uuid,entry);}
                throw failure;
            }
            if(!remote.get("state").getAsString().equals("RESERVED"))throw new IllegalStateException("Réservation bancaire indisponible.");
            entry.add("receipt",remote);entry.addProperty("local_phase","APPLYING");
            BankJournal.save(server,uuid,entry);
            server.execute(()->{
                try{
                    if(!apply.getAsBoolean()){
                        background(()->{entry.addProperty("local_phase","NO_DELIVERY");BankJournal.save(server,uuid,entry);finish(server,uuid,entry,"cancel");return null;},server,player);
                        return;
                    }
                    // Inventory snapshot is written before the local APPLIED proof.
                    // A crash between these writes is deliberately sent for reconciliation.
                    BankPlayerSnapshot.save(player);
                    background(()->{entry.addProperty("local_phase","APPLIED");BankJournal.save(server,uuid,entry);finish(server,uuid,entry,"delivery");return null;},server,player);
                }catch(Exception failure){BUSY.remove(player.getUUID());EconomyFeedback.send(player,"REVIEW","Remise à vérifier avant une autre opération","");say(player,"Opération à vérifier : reçu "+entry.get("idempotency_key").getAsString());MedievalCoin.LOGGER.error("Bank inventory delivery ambiguous",failure);}
            });
            return null;
        },server,player);
    }
    private static void finish(MinecraftServer server,String uuid,JsonObject entry,String action)throws Exception{
        JsonObject receipt=entry.getAsJsonObject("receipt");
        JsonObject result=BankProtocol.transition(uuid,receipt.get("operation_id").getAsString(),action,receipt.get("revision").getAsInt(),"mc:"+entry.get("idempotency_key").getAsString()+":"+action);
        entry.add("receipt",result);entry.addProperty("local_phase",action.equals("delivery")?"DONE":"CANCELLED");BankJournal.save(server,uuid,entry);
        long balance=BankProtocol.balance(uuid);
        server.execute(()->{
            BUSY.remove(UUID.fromString(uuid));ServerPlayer player=server.getPlayerList().getPlayer(UUID.fromString(uuid));
            if(player!=null){EconomyFeedback.send(player,"SUCCESS",action.equals("delivery")?"Opération bancaire confirmée":"Opération annulée",result.get("operation_id").getAsString());refresh(player,balance);say(player,action.equals("delivery")?"Opération bancaire confirmée. Reçu : "+result.get("operation_id").getAsString():"Opération annulée : aucune pièce déplacée.");QuestRewardService.resume(player);}
        });
    }
    private static boolean definiteRejection(Throwable failure){
        while(failure.getCause()!=null)failure=failure.getCause();
        String message=failure.getMessage();
        return failure instanceof ApiHttpClient.ApiException && message!=null && message.startsWith("HTTP 4") && !message.startsWith("HTTP 408") && !message.startsWith("HTTP 429");
    }
    private static String feedback(Throwable failure){
        while(failure.getCause()!=null)failure=failure.getCause();
        String detail=String.valueOf(failure.getMessage());
        if(detail.contains("ACCOUNT_NOT_MIGRATED"))return "Ce compte doit être préparé par un administrateur avant d’utiliser la banque.";
        if(detail.contains("INSUFFICIENT_FUNDS"))return "Le solde bancaire disponible est insuffisant. Aucune pièce remise.";
        return "Banque indisponible ou reçu à vérifier. Consultez vos finances ; reconnectez-vous pour reprendre.";
    }
    private interface Work {Object run()throws Exception;}
    private static void background(Work work,MinecraftServer server,ServerPlayer player){
        try{
            ApiExecutor.supply(()->{try{return work.run();}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}}).whenComplete((result,failure)->{
                if(failure!=null)server.execute(()->{BUSY.remove(player.getUUID());EconomyFeedback.send(player,definiteRejection(failure)?"ERROR":"REVIEW",feedback(failure),"");say(player,feedback(failure));MedievalCoin.LOGGER.warn("Bank operation pending for {}",player.getUUID(),failure);});
            });
        }catch(RuntimeException failure){BUSY.remove(player.getUUID());say(player,"Banque occupée, réessayez dans un instant.");}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        recover(player);
    }
    public static void recover(ServerPlayer player){
        if(!BUSY.add(player.getUUID()))return;
        MinecraftServer server=player.getServer();String uuid=player.getStringUUID();
        background(()->{
            for(JsonObject entry:BankJournal.pending(server,uuid)){
                if(entry.get("type").getAsString().equals("COMMERCE")){CommerceService.recover(server,uuid,entry);continue;}
                if(entry.get("type").getAsString().equals("QUEST_REWARD"))continue;
                String phase=entry.get("local_phase").getAsString();
                JsonObject receipt;
                try{receipt=BankProtocol.prepare(uuid,entry);}
                catch(Exception failure){
                    if(phase.equals("REQUESTED")&&definiteRejection(failure)){entry.addProperty("local_phase","CANCELLED");BankJournal.save(server,uuid,entry);continue;}
                    throw failure;
                }
                entry.add("receipt",receipt);
                String state=receipt.get("state").getAsString();
                if(state.equals("CONFIRMED")||state.equals("CANCELLED")){
                    entry.addProperty("local_phase",state.equals("CONFIRMED")?"DONE":"CANCELLED");BankJournal.save(server,uuid,entry);continue;
                }
                if(state.equals("RECONCILIATION_REQUIRED"))continue;
                if(phase.equals("APPLIED"))finish(server,uuid,entry,"delivery");
                else if(phase.equals("REQUESTED")||phase.equals("NO_DELIVERY"))finish(server,uuid,entry,"cancel");
                else{
                    BankProtocol.transition(uuid,receipt.get("operation_id").getAsString(),"reconcile",receipt.get("revision").getAsInt(),"ambiguous:"+entry.get("idempotency_key").getAsString());
                    server.execute(()->say(player,"Un reçu bancaire nécessite une vérification administrateur : "+receipt.get("operation_id").getAsString()));
                }
            }
            long balance=BankProtocol.balance(uuid);
            java.util.List<JsonObject> pending=BankJournal.pending(server,uuid);
            server.execute(()->{BUSY.remove(player.getUUID());if(!player.hasDisconnected()){
                refresh(player,balance);
                EconomyFeedback.send(player,pending.isEmpty()?"IDLE":"REVIEW",pending.isEmpty()?"Registre actualisé":"Un reçu doit être vérifié","");
                QuestRewardService.resume(player);
            }});return null;
        },server,player);
    }
    /** Stable caller-supplied key is mandatory for business integrations. */
    public static CompletableFuture<Long> changeBalance(ServerPlayer player,String key,long amount,boolean credit,String reason){
        String uuid=player.getStringUUID();
        try{
        return ApiExecutor.supply(()->{try{BankProtocol.change(uuid,key,amount,credit,reason);return BankProtocol.balance(uuid);}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}}).thenApply(balance->{player.getServer().execute(()->refresh(player,balance));return balance;});
        }catch(RuntimeException failure){return CompletableFuture.failedFuture(failure);}

    }
}
