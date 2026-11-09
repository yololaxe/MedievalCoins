package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterItemData;
import fr.renblood.medievalcoins.market.model.MarketTransactionDto;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketPurchaseService {
    private static final Set<UUID> PURCHASES_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private MarketPurchaseService() {
    }

    public static void purchase(ServerPlayer player, MerchantCounterBlockEntity counter, String itemId, int quantity) {
        MarketCommerceService.purchase(player,counter,itemId,quantity);
    }

    private static Optional<PreparedPurchase> validate(ServerPlayer player, MerchantCounterBlockEntity counter,
                                                       String itemId, int quantity, boolean checkMoney) {
        if (quantity < 1 || quantity > 2304) {
            fail(player, "Quantité invalide.", "quantite invalide", counter, itemId);
            return Optional.empty();
        }
        if (!counter.isOfficial()) {
            fail(player, "Ce comptoir ne permet pas encore les achats.", "type non officiel", counter, itemId);
            return Optional.empty();
        }
        if (!counter.isActive()) {
            fail(player, "Ce comptoir est fermé.", "comptoir inactif", counter, itemId);
            return Optional.empty();
        }
        if (!counter.isBuyModeEnabled()) {
            fail(player, "Les achats sont désactivés sur ce comptoir.", "achat desactive", counter, itemId);
            return Optional.empty();
        }
        MerchantCounterItemData entry = counter.purchasableItems().stream()
                .filter(value -> value.itemId().equals(itemId)).findFirst().orElse(null);
        if (entry == null || entry.unitPrice() < 1) {
            fail(player, "Prix indisponible pour cet item.", "prix indisponible", counter, itemId);
            return Optional.empty();
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) {
            fail(player, "Item indisponible.", "item inconnu", counter, itemId);
            return Optional.empty();
        }
        long total;
        try {
            total = Math.multiplyExact(entry.unitPrice(), quantity);
        } catch (ArithmeticException e) {
            fail(player, "Prix invalide.", "prix overflow", counter, itemId);
            return Optional.empty();
        }
        if (!counter.hasStock(itemId, quantity)) {
            fail(player, "Stock insuffisant.", "stock insuffisant", counter, itemId);
            return Optional.empty();
        }
        if (checkMoney && !MarketMoneyService.hasEnoughMoney(player, total)) {
            fail(player, "Vous n'avez pas assez d'argent.", "solde insuffisant", counter, itemId);
            return Optional.empty();
        }
        if (checkMoney && !MarketMoneyService.canPayAndReceive(player, total, new ItemStack(item, quantity))) {
            fail(player, "Votre inventaire n'a pas assez de place pour l'achat et la monnaie.", "inventaire ou rendu monnaie impossible", counter, itemId);
            return Optional.empty();
        }
        if (!checkMoney && !hasInventorySpace(player.getInventory(), new ItemStack(item, quantity))) {
            fail(player, "Votre inventaire est plein.", "inventaire plein", counter, itemId);
            return Optional.empty();
        }
        return Optional.of(new PreparedPurchase(entry, item, total));
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

    private static void fail(ServerPlayer player, String message, String reason, MerchantCounterBlockEntity counter, String itemId) {
        player.sendSystemMessage(Component.literal(message));
        MedievalCoin.LOGGER.warn("Tentative achat refusee: joueur={} comptoir={} item={} raison={}",
                player.getUUID(), counter.getCounterId(), itemId, reason);
    }

    private record PreparedPurchase(MerchantCounterItemData entry, Item item, long totalPrice) {
    }
}
