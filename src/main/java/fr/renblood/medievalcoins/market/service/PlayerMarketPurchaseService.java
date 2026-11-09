package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.model.MarketTransactionDto;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMarketPurchaseService {
    private static final Set<UUID> PURCHASES_IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PlayerMarketPurchaseService() {
    }

    public static void purchase(ServerPlayer buyer, MerchantCounterBlockEntity counter, String purchaseKey, int quantity) {
        MarketCommerceService.purchase(buyer,counter,purchaseKey,quantity);
    }

    private static Prepared validate(ServerPlayer buyer, MerchantCounterBlockEntity counter, int slot, int quantity, boolean money) {
        if (!counter.isPlayerMarket() || !counter.isActive() || !counter.isBuyModeEnabled() || slot < 0
                || slot >= MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS || quantity < 1 || quantity > 2304) {
            fail(buyer, "Achat invalide.", counter, "player_slot:" + slot);
            return null;
        }
        ItemStack stock = counter.playerMarketStack(slot);
        long unitPrice = counter.getPlayerSlotPrice(slot);
        if (!counter.isPlayerSlotEnabled(slot) || stock.isEmpty() || stock.getCount() < quantity) {
            fail(buyer, "Stock insuffisant.", counter, "player_slot:" + slot);
            return null;
        }
        if (unitPrice < 1) {
            fail(buyer, "Prix indisponible.", counter, "player_slot:" + slot);
            return null;
        }
        long total;
        try {
            total = Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException exception) {
            fail(buyer, "Prix invalide.", counter, "player_slot:" + slot);
            return null;
        }
        ItemStack received = stock.copy();
        received.setCount(quantity);
        if (money && !MarketMoneyService.hasEnoughMoney(buyer, total)) {
            fail(buyer, "Vous n'avez pas assez d'argent.", counter, "player_slot:" + slot);
            return null;
        }
        if (money && !MarketMoneyService.canPayAndReceive(buyer, total, received)) {
            fail(buyer, "Votre inventaire n'a pas assez de place pour l'achat et la monnaie.", counter, "player_slot:" + slot);
            return null;
        }
        if (!money && !hasInventorySpace(buyer.getInventory(), received)) {
            fail(buyer, "Votre inventaire est plein.", counter, "player_slot:" + slot);
            return null;
        }
        var id = ForgeRegistries.ITEMS.getKey(stock.getItem());
        return new Prepared(stock.copy(), id == null ? "minecraft:air" : id.toString(), unitPrice, total);
    }

    private static boolean hasInventorySpace(Inventory inventory, ItemStack stack) {
        int needed = stack.getCount();
        for (ItemStack current : inventory.items) {
            if (current.isEmpty()) needed -= stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(current, stack)) needed -= current.getMaxStackSize() - current.getCount();
            if (needed <= 0) return true;
        }
        return false;
    }

    private static int parseSlot(String key) {
        try {
            return key.startsWith("player_slot:") ? Integer.parseInt(key.substring(12)) : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static void fail(ServerPlayer player, String message, MerchantCounterBlockEntity counter, String key) {
        player.sendSystemMessage(Component.literal(message));
        MedievalCoin.LOGGER.warn("Achat PLAYER_MARKET refuse: joueur={} comptoir={} entree={}",
                player.getUUID(), counter.getCounterId(), key);
    }

    private record Prepared(ItemStack stack, String itemId, long unitPrice, long total) {
    }
}
