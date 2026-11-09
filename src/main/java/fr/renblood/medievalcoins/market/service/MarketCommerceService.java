package fr.renblood.medievalcoins.market.service;

import com.google.gson.JsonObject;
import fr.renblood.medievalcoins.bank.*;
import fr.renblood.medievalcoins.market.counter.*;
import fr.renblood.medievalcoins.network.ApiExecutor;
import fr.renblood.medievalcoins.api.MedievalCoinsAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MarketCommerceService {
    private MarketCommerceService() {}
    private static String business(MerchantCounterBlockEntity counter){return "market:"+counter.getCounterId();}
    private static String nbt(ItemStack stack){ItemStack template=stack.copy();template.setCount(1);return template.save(new CompoundTag()).toString();}
    private static String offer(int slot,ItemStack stack){return "slot:"+slot+":"+UUID.nameUUIDFromBytes(nbt(stack).getBytes(StandardCharsets.UTF_8));}
    private static void register(String uuid,MerchantCounterBlockEntity counter)throws Exception {
        String owner=null;try{owner=UUID.fromString(counter.getOwnerId()).toString();}catch(Exception ignored){}
        CommerceProtocol.register(uuid,business(counter),owner,"Comptoir "+counter.getCounterId(),"MARKET");
    }
    public static void purchase(ServerPlayer player,MerchantCounterBlockEntity counter,String itemId,int quantity){trade(player,counter,itemId,quantity,false);}
    public static void sell(ServerPlayer player,MerchantCounterBlockEntity counter,String itemId,int quantity){trade(player,counter,itemId,quantity,true);}
    private static void trade(ServerPlayer player,MerchantCounterBlockEntity counter,String itemId,int quantity,boolean sale){
        if (counter.isRemoved() || !counter.isActive()) {
            EconomyFeedback.send(player, "ERROR", "Ce comptoir est fermé.", "");
            return;
        }
        if (quantity < 1 || quantity > 2304) {
            EconomyFeedback.send(player, "ERROR", "Quantité invalide.", "");
            return;
        }
        boolean owned=counter.isPlayerMarket();
        if (owned && (!counter.isCommerceStockMirror() || sale)) {
            fr.renblood.medievalcoins.bank.EconomyFeedback.send(
                    player, "ERROR", "Ce stock doit être rapproché avant tout échange.", "");
            return;
        }
        MerchantCounterItemData selected=(sale?counter.sellableItems():counter.purchasableItems()).stream().filter(v->v.purchaseKey().equals(itemId)).findFirst().orElse(null);
        if(selected==null||selected.unitPrice()<1){fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Offre indisponible","");return;}
        int slot=-1;ItemStack received;
        if(owned){try{slot=Integer.parseInt(itemId.substring(12));}catch(Exception e){return;}
            if(quantity>64||counter.isOwner(player.getUUID())){fr.renblood.medievalcoins.bank.EconomyFeedback.send(player,"ERROR","Achat invalide sur votre propre comptoir","");return;}received=counter.playerMarketStack(slot).copy();
        }else{var id=ResourceLocation.tryParse(itemId);var item=id==null?null:ForgeRegistries.ITEMS.getValue(id);if(item==null)return;received=new ItemStack(item);}
        if(received.isEmpty())return;received.setCount(quantity);
        String uuid=player.getStringUUID(),business=business(counter),offerId=owned?offer(slot,received):itemId;
        String itemNbt=nbt(received);long unit=selected.unitPrice();
        long buy=owned?unit:counter.purchasableItems().stream().filter(v->v.itemId().equals(itemId)).mapToLong(MerchantCounterItemData::unitPrice).findFirst().orElse(unit);
        long sell=owned?0:counter.sellableItems().stream().filter(v->v.itemId().equals(itemId)).mapToLong(MerchantCounterItemData::unitPrice).findFirst().orElse(0);
        buy=Math.max(buy,sell);final long buyQuote=buy,sellQuote=sell;
        String version="commerce-v1:"+buy+":"+sell;
        CounterItemConfigData config=owned?null:counter.getConfiguredItem(itemId);
        Integer stock=owned?0:(config!=null&&"LIMITED".equals(config.stockMode())?(config.stockQuantity()==null?0:config.stockQuantity()):null);
        UUID key=UUID.randomUUID();long total;
        try{total=Math.multiplyExact(unit,quantity);}catch(ArithmeticException failure){return;}
        if (!counter.beginTransaction(-1)) {
            EconomyFeedback.send(player, "PENDING", "Ce comptoir termine déjà une opération.", "");
            return;
        }
        CommerceService.settle(player,key,()->{
            register(uuid,counter);CommerceProtocol.offer(uuid,business,offerId,version,buyQuote,sellQuote,stock,itemNbt,
                    owned ? Math.min(64, received.getMaxStackSize()) : null);
            JsonObject request=CommerceProtocol.request(business,offerId,version,sale?"SELL":"BUY",unit,quantity,"CASH");
            request.addProperty("item_id",ForgeRegistries.ITEMS.getKey(received.getItem()).toString());
            request.addProperty("item_display_name",received.getHoverName().getString());return request;
        },()->{
            if(counter.isRemoved()||!counter.isActive()||player.distanceToSqr(counter.getBlockPos().getCenter())>64)return false;
            return CommerceInventory.mutate(player,()->{
                if(sale){
                    int count=player.getInventory().items.stream().filter(s->s.getItem()==received.getItem()&&!s.hasTag()).mapToInt(ItemStack::getCount).sum();
                    if(count<quantity)return false;
                    int remaining=quantity;for(ItemStack s:player.getInventory().items){if(s.getItem()==received.getItem()&&!s.hasTag()){int take=Math.min(remaining,s.getCount());s.shrink(take);remaining-=take;}}
                    return MarketMoneyService.giveMoney(player,total);
                }
                if(!MarketMoneyService.canPayAndReceive(player,total,received))return false;
                MarketMoneyService.removeMoney(player,total).join();return addAll(player,received);
            });
        },()->{}).whenComplete((receipt,failure)->player.getServer().execute(() -> {
            counter.endTransaction(-1);
            refresh(player, counter);
        }));
    }
    private static boolean addAll(ServerPlayer player,ItemStack stack){
        int count=stack.getCount();while(count>0){ItemStack part=stack.copy();int n=Math.min(count,stack.getMaxStackSize());part.setCount(n);if(!player.getInventory().add(part))return false;count-=n;}return true;
    }
    public static void stock(ServerPlayer owner, MerchantCounterBlockEntity counter, int slot, boolean deposit) {
        if (!canMoveStock(owner, counter)) return;
        if (slot < 0 || slot >= MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS) return;
        int inventorySlot = owner.getInventory().selected;
        if (deposit) {
            startDeposit(owner, counter, List.of(new Deposit(inventorySlot, owner.getMainHandItem().copy())), slot);
            return;
        }
        ItemStack stack = counter.playerMarketStack(slot).copy();
        int count = counter.getCommerceStock(slot);
        if (stack.isEmpty() || count <= 0) {
            stockNotice(owner, "ERROR", "Cet emplacement ne contient aucun stock à reprendre.");
            return;
        }
        if (!lockStock(owner, counter)) return;
        stack.setCount(count);
        moveStock(owner, counter, slot, -1, stack, false).whenComplete((receipt, failure) -> {
            boolean confirmed = confirmed(receipt);
            if (confirmed) counter.setCommerceSlot(slot, stack, 0);
            finishStock(owner, counter, confirmed ? count + " objet(s) repris."
                    : transferError(failure, "Retrait annulé. Vérifie la place dans ton inventaire."), confirmed);
        });
    }

    public static void depositBatch(ServerPlayer owner, MerchantCounterBlockEntity counter, long selection) {
        if (!canMoveStock(owner, counter)) return;
        List<Deposit> deposits = new ArrayList<>();
        for (int slot = 0; slot < owner.getInventory().items.size(); slot++) {
            if ((selection & (1L << slot)) != 0) {
                deposits.add(new Deposit(slot, owner.getInventory().getItem(slot).copy()));
            }
        }
        startDeposit(owner, counter, deposits, -1);
    }

    private static void startDeposit(ServerPlayer owner, MerchantCounterBlockEntity counter,
                                     List<Deposit> deposits, int preferredSlot) {
        if (deposits.isEmpty()) {
            stockNotice(owner, "ERROR", "Sélectionne des piles dans ton inventaire.");
            return;
        }
        if (!lockStock(owner, counter)) return;
        depositNext(owner, counter, deposits, preferredSlot, 0, 0, 0, "", System.nanoTime());
    }

    private static void depositNext(ServerPlayer owner, MerchantCounterBlockEntity counter, List<Deposit> deposits,
                                    int preferredSlot, int index, int done, int units, String skippedReason, long started) {
        if (index >= deposits.size() || !canReachStock(owner, counter)) {
            String message = units + " objet(s) déposé(s) dans " + done + " transfert(s).";
            if (done < deposits.size()) {
                String reason = canReachStock(owner, counter) ? skippedReason : "Comptoir hors de portée.";
                message += " " + (deposits.size() - done) + " pile(s) non transférée(s). " + reason;
            }
            EconomyTimings.stockBatch(started);
            finishStock(owner, counter, message, done > 0);
            return;
        }

        Deposit deposit = deposits.get(index);
        ItemStack stack = deposit.stack();
        var validation = PlayerMarketSecurityService.validateStackForSale(stack);
        int target = validation.allowed() && ItemStack.matches(stack, owner.getInventory().getItem(deposit.inventorySlot()))
                ? depositSlot(counter, stack, preferredSlot) : -1;
        if (target < 0) {
            String reason = !validation.allowed() ? validation.message()
                    : ItemStack.matches(stack, owner.getInventory().getItem(deposit.inventorySlot()))
                    ? "Plus de place pour une pile entière dans le comptoir." : "Une pile a changé dans l’inventaire.";
            depositNext(owner, counter, deposits, preferredSlot, index + 1, done, units, reason, started);
            return;
        }

        int previousStock = counter.getCommerceStock(target);
        moveStock(owner, counter, target, deposit.inventorySlot(), stack, true).whenComplete((receipt, failure) -> {
            if (!confirmed(receipt)) {
                EconomyTimings.stockBatch(started);
                finishStock(owner, counter, units + " objet(s) déposé(s). "
                        + transferError(failure, "Dépôt annulé. La pile a changé ou le comptoir est hors de portée."), false);
                return;
            }
            counter.setCommerceSlot(target, stack, previousStock + stack.getCount());
            if ((index + 1) % 4 == 0 && index + 1 < deposits.size()) {
                stockNotice(owner, "PENDING", (index + 1) + "/" + deposits.size() + " piles traitées…");
            }
            depositNext(owner, counter, deposits, preferredSlot, index + 1, done + 1, units + stack.getCount(), skippedReason, started);
        });
    }

    private static int depositSlot(MerchantCounterBlockEntity counter, ItemStack stack, int preferredSlot) {
        if (preferredSlot >= 0 && fits(counter, preferredSlot, stack)) return preferredSlot;
        int empty = -1;
        for (int slot = 0; slot < MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS; slot++) {
            int stock = counter.getCommerceStock(slot);
            if (stock <= 0 && empty < 0 && fits(counter, slot, stack)) empty = slot;
            if (stock > 0 && fits(counter, slot, stack)) return slot;
        }
        return empty;
    }

    private static boolean fits(MerchantCounterBlockEntity counter, int slot, ItemStack stack) {
        if (counter.hasStockConflict(slot)) return false;
        int stock = counter.getCommerceStock(slot);
        int held = counter.getReservedStock(slot) + counter.getIncomingStock(slot);
        int capacity = Math.min(64, stack.getMaxStackSize());
        return stack.getCount() <= capacity && ((long) stock + held <= 0 || ((long) stock + held + stack.getCount() <= capacity
                && ItemStack.isSameItemSameTags(counter.playerMarketStack(slot), stack)));
    }

    private static CompletableFuture<JsonObject> moveStock(ServerPlayer owner, MerchantCounterBlockEntity counter,
                                                            int slot, int inventorySlot, ItemStack stack, boolean deposit) {
        String uuid = owner.getStringUUID();
        String id = offer(slot, stack);
        String itemData = nbt(stack);
        String business = business(counter);
        long price = ItemStack.isSameItemSameTags(counter.playerMarketStack(slot), stack)
                ? counter.getPlayerSlotPrice(slot) : 0;
        String version = "commerce-v1:" + price + ":0";
        return CommerceService.settleStock(owner, () -> {
            register(uuid, counter);
            // A withdrawal uses the existing stock reference and needs no quote publication.
            if (deposit) CommerceProtocol.offer(uuid, business, id, version, price, 0, 0, itemData,
                    Math.min(64, stack.getMaxStackSize()));
            return CommerceProtocol.request(business, id, version, deposit ? "STOCK_IN" : "STOCK_OUT",
                    0, stack.getCount(), "BANK");
        }, () -> {
            if (!canReachStock(owner, counter)) return false;
            if (!deposit) return CommerceInventory.mutate(owner, () -> addAll(owner, stack));
            ItemStack current = owner.getInventory().getItem(inventorySlot);
            if (!ItemStack.matches(stack, current)) return false;
            current.shrink(stack.getCount());
            owner.getInventory().setChanged();
            return true;
        });
    }

    private static boolean canMoveStock(ServerPlayer owner, MerchantCounterBlockEntity counter) {
        if (!canReachStock(owner, counter)) return false;
        if (counter.isCommerceStockMirror()) return true;
        stockNotice(owner, "ERROR", "Ce stock ancien doit être rapproché avant de pouvoir être déplacé.");
        return false;
    }

    private static boolean canReachStock(ServerPlayer owner, MerchantCounterBlockEntity counter) {
        return !owner.hasDisconnected() && !counter.isRemoved() && counter.isOwner(owner.getUUID())
                && owner.level() == counter.getLevel() && owner.distanceToSqr(counter.getBlockPos().getCenter()) <= 64;
    }

    private static boolean lockStock(ServerPlayer owner, MerchantCounterBlockEntity counter) {
        if (!counter.beginTransaction(-1)) {
            stockNotice(owner, "PENDING", "Un transfert est déjà en cours.");
            return false;
        }
        stockNotice(owner, "PENDING", "Transfert du stock en cours…");
        return true;
    }

    private static void finishStock(ServerPlayer owner, MerchantCounterBlockEntity counter, String message, boolean success) {
        counter.endTransaction(-1);
        stockNotice(owner, success ? "SUCCESS" : "ERROR", message);
        if (canReachStock(owner, counter)) {
            if (owner.containerMenu instanceof MerchantCounterOwnerMenu menu && menu.getPos().equals(counter.getBlockPos())) {
                MerchantCounterMenus.open(owner, counter, true);
            }
            refresh(owner, counter);
        }
    }

    private static void stockNotice(ServerPlayer owner, String state, String message) {
        EconomyFeedback.send(owner, state, message, "");
    }

    private static boolean confirmed(JsonObject receipt) {
        return receipt != null && "CONFIRMED".equals(receipt.get("state").getAsString());
    }

    private static String transferError(Throwable failure, String fallback) {
        return failure == null || failure.getMessage() == null ? fallback : failure.getMessage();
    }

    private record Deposit(int inventorySlot, ItemStack stack) {}

    public static boolean claim(ServerPlayer owner,MerchantCounterBlockEntity counter){
        if(!counter.isOwner(owner.getUUID()))return false;
        MedievalCoinsAPI.claimCommerce(owner,UUID.randomUUID().toString(),business(counter));return true;
    }
    public static void refresh(ServerPlayer player,MerchantCounterBlockEntity counter){
        String uuid = player.getStringUUID();
        var server = player.getServer();
        var openedMenu = player.containerMenu;
        long revision = counter.getTransactionRevision();
        String businessId = business(counter);
        boolean playerMarket = counter.isPlayerMarket();
        try {
            ApiExecutor.supply(() -> {
                try {
                    var offers = CommerceProtocol.stockOffers(uuid, businessId, playerMarket);
                    server.execute(() -> {
                        if (counter.isRemoved() || counter.isTransactionInProgress()
                                || revision != counter.getTransactionRevision() || playerMarket != counter.isPlayerMarket()) return;
                        boolean stockChanged = playerMarket ? updatePlayerStock(counter, offers)
                                : updateOfficialStock(counter, offers.rows());
                        if (stockChanged && !player.hasDisconnected() && player.containerMenu == openedMenu
                                && openedMenu instanceof AbstractMerchantCounterMenu menu
                                && !(menu instanceof MerchantCounterAdminMenu)
                                && menu.getPos().equals(counter.getBlockPos())
                                && player.level() == counter.getLevel() && menu.stillValid(player)) {
                            MerchantCounterMenus.open(player, counter, menu instanceof MerchantCounterOwnerMenu);
                        }
                    });
                } catch (Exception ignored) {
                    // Keep the last known stock when the backend is unavailable.
                }
                return null;
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // A later opening or transaction can refresh again.
        }
    }

    private static boolean updatePlayerStock(MerchantCounterBlockEntity counter, CommerceProtocol.StockOffers offers) {
        if (!counter.isCommerceStockMirror()) return false;
        Map<Integer, SlotStock> bySlot = new HashMap<>();
        try {
            for (JsonObject row : offers.rows()) {
                String id = row.get("offer_id").getAsString();
                if (!id.startsWith("slot:")) continue;
                int slot = Integer.parseInt(id.split(":", 3)[1]);
                if (slot < 0 || slot >= MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS) return false;
                int stock = row.get("stock").getAsInt();
                if (stock < 0) return false;
                int reserved = row.has("reserved_stock") ? row.get("reserved_stock").getAsInt() : 0;
                int incoming = row.has("incoming_stock") ? row.get("incoming_stock").getAsInt() : 0;
                if (reserved < 0 || incoming < 0) return false;
                if ((long) stock + reserved + incoming == 0 && (offers.complete() || counter.playerMarketStack(slot).isEmpty()
                        || !offer(slot, counter.playerMarketStack(slot)).equals(id))) continue;
                ItemStack template = ItemStack.of(TagParser.parseTag(row.get("item_nbt").getAsString()));
                if (template.isEmpty()) return false;
                int capacity = Math.min(64, template.getMaxStackSize());
                boolean overCapacity = (long) stock + reserved + incoming > capacity;
                String receipt = row.has("pending_receipt") ? row.get("pending_receipt").getAsString() : "";
                SlotStock previous = bySlot.get(slot);
                if (previous != null) {
                    bySlot.put(slot, new SlotStock(previous.template(), previous.count(),
                            Math.addExact(previous.reserved(), reserved), Math.addExact(previous.incoming(), incoming),
                            previous.receipt().isEmpty() ? receipt : previous.receipt(), true));
                } else {
                    bySlot.put(slot, new SlotStock(template, stock, reserved, incoming, receipt, overCapacity));
                }
            }
        } catch (Exception malformed) {
            return false;
        }

        boolean changed = false;
        for (int slot = 0; slot < MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS; slot++) {
            SlotStock stock = bySlot.get(slot);
            ItemStack previous = counter.playerMarketStack(slot);
            if (stock == null && !offers.complete()) continue;
            changed |= counter.setCommerceHolds(slot, stock == null ? 0 : stock.reserved(),
                    stock == null ? 0 : stock.incoming(), stock == null ? "" : stock.receipt(), stock != null && stock.conflict());
            if (stock != null && stock.conflict()) continue;
            int quantity = stock == null ? 0 : stock.count();
            ItemStack template = stock == null ? previous : stock.template();
            if (quantity != counter.getCommerceStock(slot) || !ItemStack.isSameItemSameTags(previous, template)) {
                counter.setCommerceSlot(slot, template, quantity);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateOfficialStock(MerchantCounterBlockEntity counter, List<JsonObject> offers) {
        boolean changed = false;
        for (JsonObject row : offers) {
            if (row.get("stock").isJsonNull()) continue;
            String id = row.get("offer_id").getAsString();
            var configured = counter.getConfiguredItem(id);
            int stock = row.get("stock").getAsInt();
            if (configured != null && "LIMITED".equals(configured.stockMode())
                    && !java.util.Objects.equals(configured.stockQuantity(), stock)) {
                counter.setCommerceOfficialStock(id, stock);
                changed = true;
            }
        }
        return changed;
    }

    private record SlotStock(ItemStack template, int count, int reserved, int incoming, String receipt, boolean conflict) {}
}
