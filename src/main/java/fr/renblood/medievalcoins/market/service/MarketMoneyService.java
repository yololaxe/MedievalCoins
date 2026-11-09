package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MarketMoneyService {
    public static final long IRON_COIN_VALUE = 1L;
    public static final long BRONZE_COIN_VALUE = 64L;
    public static final long SILVER_COIN_VALUE = 4096L;
    public static final long GOLD_COIN_VALUE = 262144L;

    private MarketMoneyService() {}

    public static long getPlayerBalanceInIron(ServerPlayer player) {
        long iron = player.getInventory().items.stream().filter(s->s.is(Coins.IRON_COIN.get())&&!s.hasTag()).mapToLong(ItemStack::getCount).sum();
        long bronze = player.getInventory().items.stream().filter(s->s.is(Coins.BRONZE_COIN.get())&&!s.hasTag()).mapToLong(ItemStack::getCount).sum();
        long silver = player.getInventory().items.stream().filter(s->s.is(Coins.SILVER_COIN.get())&&!s.hasTag()).mapToLong(ItemStack::getCount).sum();
        long gold = player.getInventory().items.stream().filter(s->s.is(Coins.GOLD_COIN.get())&&!s.hasTag()).mapToLong(ItemStack::getCount).sum();
        long total = iron + bronze * BRONZE_COIN_VALUE + silver * SILVER_COIN_VALUE + gold * GOLD_COIN_VALUE;
        if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info(
                "MarketMoney balance={} [fer={}, bronze={}, argent={}, or={}]", total, iron, bronze, silver, gold);
        return total;
    }

    public static boolean hasEnoughMoney(ServerPlayer player, long amountInIron) {
        return amountInIron > 0 && getPlayerBalanceInIron(player) >= amountInIron;
    }

    public static boolean canRemoveMoney(ServerPlayer player, long amountInIron) {
        long balance = getPlayerBalanceInIron(player);
        return amountInIron > 0 && balance >= amountInIron
                && canFitChangeAfterRemovingCoins(player.getInventory(), balance - amountInIron);
    }

    public static boolean canPayAndReceive(ServerPlayer player, long amountInIron, ItemStack purchased) {
        long balance = getPlayerBalanceInIron(player);
        if (amountInIron < 1 || balance < amountInIron) return false;
        int emptyAfterCoinRemoval = 0;
        int itemMergeCapacity = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || (isCoin(stack.getItem()) && !stack.hasTag())) emptyAfterCoinRemoval++;
            else if (ItemStack.isSameItemSameTags(stack, purchased)) itemMergeCapacity += stack.getMaxStackSize() - stack.getCount();
        }
        int changeSlots = changeStacks(balance - amountInIron).values().stream().mapToInt(value -> (value + 63) / 64).sum();
        int remainingItems = Math.max(0, purchased.getCount() - itemMergeCapacity);
        int itemSlots = (remainingItems + purchased.getMaxStackSize() - 1) / purchased.getMaxStackSize();
        return changeSlots + itemSlots <= emptyAfterCoinRemoval;
    }

    public static CompletableFuture<PaymentResult> removeMoney(ServerPlayer player, long amountInIron) {
        long balance = getPlayerBalanceInIron(player);
        if (amountInIron < 1 || balance < amountInIron) return CompletableFuture.failedFuture(new IllegalStateException("Solde insuffisant"));
        long change = balance - amountInIron;
        if (!canFitChangeAfterRemovingCoins(player.getInventory(), change)) return CompletableFuture.failedFuture(new ChangeSpaceException());
        removeAllCoins(player.getInventory());
        if (!giveChange(player, change)) {
            giveChange(player, balance);
            return CompletableFuture.failedFuture(new ChangeSpaceException());
        }
        if (MedievalCoin.DEBUG_MODE) MedievalCoin.LOGGER.info(
                "MarketMoney paiement={} balanceAvant={} monnaieRendue={}", amountInIron, balance, change);
        return CompletableFuture.completedFuture(new PaymentResult(change, change));
    }

    public static void refundMoney(ServerPlayer player, long amountInIron) {
        if (!giveChange(player, amountInIron)) {
            MedievalCoin.LOGGER.error("Remboursement inventaire impossible pour {}: {}", player.getUUID(), amountInIron);
        }
    }

    public static boolean canGiveMoney(ServerPlayer player, long amountInIron) {
        if (amountInIron < 1 || amountInIron / GOLD_COIN_VALUE > player.getInventory().items.size() * 64L) {
            return false;
        }
        int requiredSlots = 0;
        for (Map.Entry<Item, Integer> entry : changeStacks(amountInIron).entrySet()) {
            int remaining = entry.getValue();
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(entry.getKey()) && !stack.hasTag()) remaining -= stack.getMaxStackSize() - stack.getCount();
            }
            requiredSlots += (Math.max(0, remaining) + 63) / 64;
        }
        int emptySlots = (int) player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
        return requiredSlots <= emptySlots;
    }

    public static boolean giveMoney(ServerPlayer player, long amountInIron) {
        return amountInIron > 0 && canGiveMoney(player, amountInIron) && giveChange(player, amountInIron);
    }

    public static boolean giveChange(ServerPlayer player, long amountInIron) {
        for (Map.Entry<Item, Integer> entry : changeStacks(amountInIron).entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int count = Math.min(64, remaining);
                if (!player.getInventory().add(new ItemStack(entry.getKey(), count))) return false;
                remaining -= count;
            }
        }
        return true;
    }

    private static boolean canFitChangeAfterRemovingCoins(Inventory inventory, long change) {
        int available = 0;
        for (ItemStack stack : inventory.items) if (stack.isEmpty() || (isCoin(stack.getItem()) && !stack.hasTag())) available++;
        int required = changeStacks(change).values().stream().mapToInt(value -> (value + 63) / 64).sum();
        return required <= available;
    }

    private static Map<Item, Integer> changeStacks(long amount) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        add(result, Coins.GOLD_COIN.get(), amount / GOLD_COIN_VALUE); amount %= GOLD_COIN_VALUE;
        add(result, Coins.SILVER_COIN.get(), amount / SILVER_COIN_VALUE); amount %= SILVER_COIN_VALUE;
        add(result, Coins.BRONZE_COIN.get(), amount / BRONZE_COIN_VALUE); amount %= BRONZE_COIN_VALUE;
        add(result, Coins.IRON_COIN.get(), amount);
        return result;
    }

    private static void add(Map<Item, Integer> result, Item item, long count) {
        if (count > 0) result.put(item, Math.toIntExact(count));
    }

    private static void removeAllCoins(Inventory inventory) {
        for (int i = 0; i < inventory.items.size(); i++) if (isCoin(inventory.items.get(i).getItem()) && !inventory.items.get(i).hasTag()) inventory.items.set(i, ItemStack.EMPTY);
        inventory.setChanged();
    }

    private static boolean isCoin(Item item) {
        return item == Coins.IRON_COIN.get() || item == Coins.BRONZE_COIN.get()
                || item == Coins.SILVER_COIN.get() || item == Coins.GOLD_COIN.get();
    }

    public static String formatMoney(long amount) {
        long gold = amount / GOLD_COIN_VALUE; amount %= GOLD_COIN_VALUE;
        long silver = amount / SILVER_COIN_VALUE; amount %= SILVER_COIN_VALUE;
        long bronze = amount / BRONZE_COIN_VALUE; long iron = amount % BRONZE_COIN_VALUE;
        StringBuilder value = new StringBuilder();
        append(value, gold, "or"); append(value, silver, "argent"); append(value, bronze, "bronze"); append(value, iron, "fer");
        return value.length() == 0 ? "0 fer" : value.toString();
    }

    private static void append(StringBuilder result, long value, String name) {
        if (value == 0) return;
        if (result.length() > 0) result.append(' ');
        result.append(value).append(' ').append(name);
    }

    public record PaymentResult(long remainingBalance, long changeGiven) {}
    public static class ChangeSpaceException extends IllegalStateException {}
}
