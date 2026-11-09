package fr.renblood.medievalcoins.bank;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.ApiExecutor;
import fr.renblood.medievalcoins.network.ApiHttpClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** One journal for each physical settlement. No network calls on the tick thread. */
public final class CommerceService {
    private CommerceService() {}
    /** Already consumed passive service: only cashbox legs, no player/world mutation. */
    public static CompletableFuture<JsonObject> financial(MinecraftServer server,String uuid,UUID key,Callable<JsonObject> prepare){
        CompletableFuture<JsonObject> result=new CompletableFuture<>();
        background(()->{
            JsonObject entry=BankJournal.find(server,uuid,key.toString());
            if(entry==null){
                entry=new JsonObject();entry.addProperty("type","COMMERCE");entry.addProperty("financial_only",true);
                entry.addProperty("idempotency_key",key.toString());entry.addProperty("local_phase","REQUESTED");
                JsonObject request=prepare.call();request.addProperty("idempotency_key",key.toString());entry.add("request",request);BankJournal.save(server,uuid,entry);
            }
            if(entry.get("local_phase").getAsString().equals("DONE")||entry.get("local_phase").getAsString().equals("CANCELLED")){result.complete(entry.getAsJsonObject("receipt"));return null;}
            JsonObject receipt;
            try{receipt=CommerceProtocol.post(uuid,"commerce/operations",entry.getAsJsonObject("request"));}
            catch(Exception failure){if(definite(failure)){entry.addProperty("local_phase","CANCELLED");BankJournal.save(server,uuid,entry);}throw failure;}
            entry.add("receipt",receipt);
            if(receipt.get("state").getAsString().equals("RESERVED"))result.complete(finish(server,uuid,entry,"delivery"));
            else if(receipt.get("state").getAsString().equals("CONFIRMED")||receipt.get("state").getAsString().equals("CANCELLED")){
                entry.addProperty("local_phase",receipt.get("state").getAsString().equals("CONFIRMED")?"DONE":"CANCELLED");BankJournal.save(server,uuid,entry);result.complete(receipt);
            }else throw new IllegalStateException("Service à rapprocher");
            return null;
        },result);return result;
    }
    public static CompletableFuture<JsonObject> settle(ServerPlayer player,UUID key,Callable<JsonObject> prepare,BooleanSupplier delivery,Runnable persistBusiness) {
        return settle(player, key, prepare, delivery, persistBusiness, false, false, false);
    }

    public static CompletableFuture<JsonObject> settleContract(ServerPlayer player, UUID key,
            Callable<JsonObject> prepare, BooleanSupplier delivery) {
        return settle(player, key, prepare, delivery, () -> {}, true, false, false);
    }

    public static CompletableFuture<JsonObject> settleCustomer(ServerPlayer player, UUID key,
            Callable<JsonObject> prepare, BooleanSupplier delivery, Runnable persistBusiness) {
        return settle(player, key, prepare, delivery, persistBusiness, true, true, false);
    }

    public static CompletableFuture<JsonObject> settleStock(ServerPlayer player,
            Callable<JsonObject> prepare, BooleanSupplier delivery) {
        return settle(player, UUID.randomUUID(), prepare, delivery, () -> {}, false, false, true);
    }

    private static CompletableFuture<JsonObject> settle(ServerPlayer player, UUID key,
            Callable<JsonObject> prepare, BooleanSupplier delivery, Runnable persistBusiness,
            boolean retryCancelled, boolean customerService, boolean stockTransfer) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        var activeKey = new java.util.concurrent.atomic.AtomicReference<>(key);
        MinecraftServer server=player.getServer();String uuid=player.getStringUUID();
        if(!server.isSameThread())return CompletableFuture.failedFuture(new IllegalStateException("Commerce must start on the server thread"));
        if(!BankService.acquire(player))return CompletableFuture.failedFuture(new IllegalStateException("Une opération est déjà en cours."));
        CompletableFuture<JsonObject> stockResult = new CompletableFuture<>();
        if (!stockTransfer) EconomyFeedback.send(player,"PENDING","Vérification et réservation en cours…",key.toString());
        result.whenComplete((receipt,failure)->server.execute(()->{
            BankService.release(player);
            if (stockTransfer) {
                if (failure == null) {
                    stockResult.complete(receipt);
                } else {
                    String message = feedback(failure, activeKey.get());
                    MedievalCoin.LOGGER.warn("Commerce stock receipt {} pending", activeKey.get(), failure);
                    stockResult.completeExceptionally(new IllegalStateException(message, failure));
                }
                return;
            }
            if (failure != null) {
                UUID failedKey = activeKey.get();
                String message = feedback(failure, failedKey);
                EconomyFeedback.send(player, definite(failure) ? "ERROR" : "REVIEW", message, failedKey.toString());
                if (!customerService) {
                    player.sendSystemMessage(Component.literal(message).withStyle(net.minecraft.ChatFormatting.RED));
                }
                MedievalCoin.LOGGER.warn("Commerce receipt {} pending", failedKey, failure);
            }
            else {
                boolean confirmed = "CONFIRMED".equals(receipt.get("state").getAsString());
                String reference = receipt.get("operation_id").getAsString();
                EconomyFeedback.send(player, confirmed ? "SUCCESS" : "ERROR",
                        confirmed ? "Règlement confirmé" : "Règlement annulé", reference);
                if (!customerService) {
                    player.sendSystemMessage(Component.literal("Échange " + (confirmed ? "confirmé" : "annulé")
                            + ". Reçu : " + reference).withStyle(confirmed ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                    if (receipt.has("payment") && "BANK".equals(receipt.get("payment").getAsString())
                            && receipt.get("amount").getAsLong() > 0) BankService.refreshBalance(player);
                    QuestRewardService.resume(player);
                }
            }
        }));
        background(()->{
            if(BankJournal.pending(server,uuid).stream().anyMatch(e->!e.get("type").getAsString().equals("QUEST_REWARD")||!e.get("local_phase").getAsString().equals("REQUESTED")))throw new IllegalStateException("Une opération doit être rapprochée avant un nouvel échange.");
            UUID requestKey = availableKey(server, uuid, key, retryCancelled);
            activeKey.set(requestKey);
            JsonObject request = prepare.call();
            request.addProperty("idempotency_key", requestKey.toString());
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "COMMERCE");
            entry.addProperty("idempotency_key", requestKey.toString());
            entry.add("request", request);
            entry.addProperty("local_phase","REQUESTED");BankJournal.save(server,uuid,entry);
            JsonObject receipt;
            try{receipt=CommerceProtocol.post(uuid,"commerce/operations",request);}
            catch(Exception failure){if(definite(failure)){entry.addProperty("local_phase","CANCELLED");BankJournal.save(server,uuid,entry);}throw failure;}
            entry.add("receipt",receipt);
            if(!receipt.get("state").getAsString().equals("RESERVED"))throw new IllegalStateException("Reçu déjà traité : aucune remise répétée.");
            entry.addProperty("local_phase","APPLYING");BankJournal.save(server,uuid,entry);
            server.execute(()->{
                try{
                    boolean connected=!player.hasDisconnected()&&server.getPlayerList().getPlayer(player.getUUID())==player;
                    if(!connected||!delivery.getAsBoolean()){
                        background(()->{entry.addProperty("local_phase","NO_DELIVERY");BankJournal.save(server,uuid,entry);result.complete(finish(server,uuid,entry,"cancel"));return null;},result);
                        return;
                    }
                    persistBusiness.run();BankPlayerSnapshot.save(player);
                    background(()->{entry.addProperty("local_phase","APPLIED");BankJournal.save(server,uuid,entry);result.complete(finish(server,uuid,entry,"delivery"));return null;},result);
                }catch(Exception failure){result.completeExceptionally(failure);}
            });return null;
        },result);
        return stockTransfer ? stockResult : result;
    }
    private static UUID availableKey(MinecraftServer server, String player, UUID initial,
                                     boolean retryCancelled) throws Exception {
        UUID candidate = initial;
        for (int attempt = 0; attempt < 128; attempt++) {
            JsonObject previous = BankJournal.find(server, player, candidate.toString());
            if (previous == null) return candidate;
            if (!retryCancelled || !"CANCELLED".equals(previous.get("local_phase").getAsString())) {
                throw new IllegalStateException("Cette demande existe déjà.");
            }
            // A successor is possible only after the journal has archived a definite cancellation.
            // The contract's offer identifier remains unchanged for the backend redemption lock.
            candidate = UUID.nameUUIDFromBytes((candidate + ":cancelled").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("Trop de tentatives pour ce contrat.");
    }

    private static void background(Callable<?> work,CompletableFuture<?> result){
        try{ApiExecutor.supply(()->{try{return work.call();}catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}})
            .whenComplete((unused,failure)->{if(failure!=null)result.completeExceptionally(failure);});}
        catch(RuntimeException failure){result.completeExceptionally(failure);}
    }
    private static boolean definite(Throwable failure){
        while(failure.getCause()!=null)failure=failure.getCause();String m=String.valueOf(failure.getMessage());
        return failure instanceof ApiHttpClient.ApiException&&m.startsWith("HTTP 4")&&!m.startsWith("HTTP 408")&&!m.startsWith("HTTP 429");
    }
    private static String feedback(Throwable failure,UUID key){
        while(failure.getCause()!=null)failure=failure.getCause();String detail=String.valueOf(failure.getMessage());
        if(detail.contains("INSUFFICIENT_FUNDS"))return "Budget du marchand ou solde bancaire insuffisant. Aucun échange effectué.";
        if(detail.contains("SLOT_OCCUPIED"))return "Cette case retient encore un autre article ou un reçu. Choisis une autre case ou consulte le support.";
        if(detail.contains("STOCK_LIMIT"))return "Cet emplacement est plein.";
        if(detail.contains("OWNER_NOT_LINKED"))return "Le compte Minecraft du propriétaire doit être lié au site.";
        if(detail.contains("OWNER_REQUIRED") || detail.contains("BUSINESS_CONFLICT"))return "Le propriétaire de ce comptoir ne correspond pas à celui enregistré dans l’économie.";
        if(detail.contains("doit être rapprochée"))return detail;
        if(detail.contains("OUT_OF_STOCK"))return "Cet objet n’est plus disponible dans cette quantité. Rechargez le comptoir.";
        if(detail.contains("PRICE_CHANGED"))return "Le prix a changé. Rechargez l’offre avant de confirmer.";
        if(detail.contains("ACCOUNT_NOT_MIGRATED"))return "Le compte bancaire doit être préparé par un administrateur.";
        return "Échange non terminé. Vérifiez le reçu "+key+" avant de recommencer.";
    }
    private static JsonObject finish(MinecraftServer server,String uuid,JsonObject entry,String action)throws Exception {
        JsonObject receipt=entry.getAsJsonObject("receipt");
        JsonObject updated=BankProtocol.transition(uuid,receipt.get("operation_id").getAsString(),action,receipt.get("revision").getAsInt(),"commerce:"+entry.get("idempotency_key").getAsString()+":"+action);
        entry.add("receipt",updated);entry.addProperty("local_phase",action.equals("delivery")?"DONE":"CANCELLED");BankJournal.save(server,uuid,entry);return updated;
    }
    /** Called under the bank's shared per-player lock during login. Never re-delivers items. */
    public static void recover(MinecraftServer server,String uuid,JsonObject entry)throws Exception {
        String phase=entry.get("local_phase").getAsString();JsonObject receipt;
        try{receipt=CommerceProtocol.post(uuid,"commerce/operations",entry.getAsJsonObject("request"));}
        catch(Exception failure){if(phase.equals("REQUESTED")&&definite(failure)){entry.addProperty("local_phase","CANCELLED");BankJournal.save(server,uuid,entry);return;}throw failure;}
        entry.add("receipt",receipt);String state=receipt.get("state").getAsString();
        if(state.equals("CONFIRMED")||state.equals("CANCELLED")){
            entry.addProperty("local_phase",state.equals("CONFIRMED")?"DONE":"CANCELLED");BankJournal.save(server,uuid,entry);return;
        }
        if(state.equals("RECONCILIATION_REQUIRED"))return;
        if(phase.equals("APPLIED")||(entry.has("financial_only")&&entry.get("financial_only").getAsBoolean()))finish(server,uuid,entry,"delivery");
        else if(phase.equals("REQUESTED")||phase.equals("NO_DELIVERY"))finish(server,uuid,entry,"cancel");
        else BankProtocol.transition(uuid,receipt.get("operation_id").getAsString(),"reconcile",receipt.get("revision").getAsInt(),"ambiguous:"+entry.get("idempotency_key").getAsString());
    }
}
