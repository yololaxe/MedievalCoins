package fr.renblood.medievalcoins.bank;

import com.google.gson.*;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiHttpClient;
import fr.renblood.medievalcoins.market.service.MarketMoneyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.TagParser;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Called reflectively by NPC Shopkeeper. HTTP methods run only in its bounded worker. */
public final class NpcCommerceGateway {
    private NpcCommerceGateway() {}
    public static JsonObject ticketStatus(ServerPlayer player,String key)throws Exception{
        JsonObject entry=BankJournal.find(player.getServer(),player.getStringUUID(),key);
        if(entry==null||!entry.has("receipt"))return new JsonObject();
        String id=entry.getAsJsonObject("receipt").get("operation_id").getAsString();
        return CommerceProtocol.get(player.getStringUUID(),"operations/"+id);
    }
    public static JsonObject request(String minecraft, String path, JsonObject body) throws Exception {
        if (!path.startsWith("npc/")) throw new IllegalArgumentException("NPC endpoint required");
        String base=ModConfig.load().apiUrl.replaceAll("/+$","")+"/api/economy/v1/";
        try{return JsonParser.parseString(ApiHttpClient.minecraftJson(body==null?"GET":"POST",base+path,body,minecraft)).getAsJsonObject();}
        catch(ApiHttpClient.ApiException failure){String raw=failure.getMessage();int start=raw==null?-1:raw.indexOf('{');if(start>=0)try{JsonObject error=JsonParser.parseString(raw.substring(start)).getAsJsonObject();if(error.has("message"))throw new IllegalStateException(error.get("message").getAsString());}catch(JsonParseException ignored){}
            throw new IllegalStateException("Le service est indisponible. Vérifie les droits et la liaison du personnage.",failure);}
    }
    public static CompletableFuture<JsonObject> shop(ServerPlayer player,String id,JsonObject wanted,BooleanSupplier nearby) {
        JsonObject[] selected=new JsonObject[1];
        int quantity=wanted.get("quantity").getAsInt();
        if(quantity<1||quantity>4096)return CompletableFuture.failedFuture(new IllegalArgumentException("Quantité invalide"));
        boolean selling="SELL".equals(wanted.get("action").getAsString());
        UUID key=UUID.fromString(wanted.get("key").getAsString());
        return CommerceService.settle(player,key,()->{
            JsonObject catalogue=request(player.getStringUUID(),"npc/shops/"+id,null);
            for(var value:catalogue.getAsJsonArray("offers")) {
                JsonObject row=value.getAsJsonObject();
                if(!row.get("id").getAsString().equals(wanted.get("offer_id").getAsString()))continue;
                if(!row.get("price_version").getAsString().equals(wanted.get("price_version").getAsString()))throw new IllegalStateException("Le prix a changé. Recharge la boutique.");
                selected[0]=row;
                long unit=row.get(selling?"sell_price":"buy_price").getAsLong();
                if(unit<1)throw new IllegalStateException("Échange désactivé");
                return CommerceProtocol.request("npcshop:"+id,row.get("id").getAsString(),row.get("price_version").getAsString(),selling?"SELL":"BUY",unit,quantity,"CASH");
            }
            throw new IllegalStateException("Article indisponible");
        },()->{
            if(!nearby.getAsBoolean()||selected[0]==null)return false;
            try {
                ItemStack exemplar=ItemStack.of(TagParser.parseTag(selected[0].get("nbt").getAsString()));
                if(exemplar.isEmpty())return false;
                long total=Math.multiplyExact(selected[0].get(selling?"sell_price":"buy_price").getAsLong(),quantity);
                boolean delivered=CommerceInventory.mutate(player,()->{
                    if(selling){
                        int count=player.getInventory().items.stream().filter(s->ItemStack.isSameItemSameTags(s,exemplar)).mapToInt(ItemStack::getCount).sum();
                        if(count<quantity)return false;
                        int left=quantity;
                        for(var stack:player.getInventory().items){if(ItemStack.isSameItemSameTags(stack,exemplar)){int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;if(left==0)break;}}
                        return MarketMoneyService.giveMoney(player,total);
                    }
                    if(!MarketMoneyService.canRemoveMoney(player,total))return false;
                    MarketMoneyService.removeMoney(player,total).join();
                    int left=quantity;
                    while(left>0){ItemStack part=exemplar.copy();int size=Math.min(left,part.getMaxStackSize());part.setCount(size);if(!player.getInventory().add(part))return false;left-=size;}
                    return true;
                });
                if(!delivered)player.sendSystemMessage(net.minecraft.network.chat.Component.literal(selling?"Vérifie la quantité et les propriétés des objets, puis libère de la place pour les pièces.":"Vérifie tes pièces et libère de la place pour les articles et la monnaie.").withStyle(net.minecraft.ChatFormatting.RED));
                return delivered;
            }catch(Exception failure){return false;}
        },()->{});
    }
    public static CompletableFuture<JsonObject> ticket(ServerPlayer player,String key,String port,long price,
            BooleanSupplier valid,Runnable issue,Runnable persist,boolean refund,String original) {
        String business="passage:"+port;
        return CommerceService.settle(player,UUID.fromString(key),()->{
            CommerceProtocol.register(player.getStringUUID(),business,null,"Traversée — "+port,"NPC");
            if(refund){JsonObject body=new JsonObject();body.addProperty("business_id",business);body.addProperty("action","REFUND");body.addProperty("original_key",original);body.addProperty("payment","CASH");return body;}
            String offer="ticket:"+key,version="passage-v1:"+price;
            CommerceProtocol.offer(player.getStringUUID(),business,offer,version,price,0,null,null);
            return CommerceProtocol.request(business,offer,version,"SERVICE",price,1,"CASH");
        },()->{
            if(!valid.getAsBoolean())return false;
            boolean changed=CommerceInventory.mutate(player,()->{
                if(refund)return MarketMoneyService.giveMoney(player,price);
                if(!MarketMoneyService.canRemoveMoney(player,price))return false;
                MarketMoneyService.removeMoney(player,price).join();return true;
            });
            if(!changed)return false;
            issue.run();return true;
        },persist);
    }
}
