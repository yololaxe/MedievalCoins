package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterItemData;
import fr.renblood.medievalcoins.market.counter.CounterItemConfigData;
import fr.renblood.medievalcoins.market.model.MarketTransactionDto;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public final class MarketSellService {
    private MarketSellService() {}

    public static void sell(ServerPlayer player, MerchantCounterBlockEntity counter, String itemId, int quantity) {
        MarketCommerceService.sell(player,counter,itemId,quantity);
    }

    private static Optional<PreparedSale> validate(ServerPlayer player, MerchantCounterBlockEntity counter,
                                                   String itemId, int quantity) {
        if (quantity < 1 || quantity > 2304) return failed(player, "Quantité invalide.", "quantite invalide", counter, itemId);
        if (!counter.isOfficial()) return failed(player, "Ce comptoir ne rachète pas d'items.", "type non officiel", counter, itemId);
        if (!counter.isActive()) return failed(player, "Ce comptoir est fermé.", "comptoir inactif", counter, itemId);
        if (!counter.isSellModeEnabled()) return failed(player, "Ce comptoir ne rachète pas d'items.", "vente desactivee", counter, itemId);
        CounterItemConfigData configured = counter.getConfiguredItem(itemId);
        if (configured == null || !configured.active() || !configured.sellEnabled()) {
            return failed(player, "Ce comptoir ne rachète pas cet item.", "item non accepte", counter, itemId);
        }
        MerchantCounterItemData entry = counter.sellableItems().stream().filter(value -> value.itemId().equals(itemId)).findFirst().orElse(null);
        if (entry == null || entry.unitPrice() < 1) return failed(player, "Prix de rachat indisponible.", "prix introuvable", counter, itemId);
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) return failed(player, "Ce comptoir ne rachète pas cet item.", "item inconnu", counter, itemId);
        int clean = countCleanItems(player, item);
        int all = player.getInventory().countItem(item);
        if (clean < quantity) {
            if (all >= quantity) return failed(player, "Cet item ne peut pas être vendu car il contient des données spéciales.", "item NBT refuse", counter, itemId);
            return failed(player, "Vous ne possédez pas assez de cet item.", "quantite insuffisante", counter, itemId);
        }
        long total;
        try {
            total = Math.multiplyExact(entry.unitPrice(), quantity);
        } catch (ArithmeticException exception) {
            return failed(player, "Prix de rachat indisponible.", "prix overflow", counter, itemId);
        }
        if (!MarketMoneyService.canGiveMoney(player, total)) {
            return failed(player, "Votre inventaire n'a pas assez de place pour recevoir l'argent.", "espace paiement insuffisant", counter, itemId);
        }
        return Optional.of(new PreparedSale(entry, item, total));
    }

    private static int countCleanItems(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item) && !stack.hasTag())
                .mapToInt(ItemStack::getCount).sum();
    }

    private static void removeCleanItems(ServerPlayer player, Item item, int quantity) {
        int remaining = quantity;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining == 0) break;
            if (stack.is(item) && !stack.hasTag()) {
                int removed = Math.min(stack.getCount(), remaining);
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        player.getInventory().setChanged();
    }

    private static Optional<PreparedSale> failed(ServerPlayer player, String message, String reason,
                                                 MerchantCounterBlockEntity counter, String itemId) {
        fail(player, message, reason, counter, itemId);
        return Optional.empty();
    }

    private static void fail(ServerPlayer player, String message, String reason,
                             MerchantCounterBlockEntity counter, String itemId) {
        player.sendSystemMessage(Component.literal(message));
        MedievalCoin.LOGGER.warn("Tentative vente refusee: joueur={} comptoir={} item={} raison={}",
                player.getUUID(), counter.getCounterId(), itemId, reason);
    }

    private record PreparedSale(MerchantCounterItemData entry, Item item, long totalPrice) {}
}
