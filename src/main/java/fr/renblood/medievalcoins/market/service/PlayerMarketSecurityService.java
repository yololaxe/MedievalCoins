package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.config.MarketConfig;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PlayerMarketSecurityService {
    private PlayerMarketSecurityService() {
    }

    public static ValidationResult validateCounterCreation(ServerPlayer player) {
        MarketConfig config = MarketConfig.load();
        if (!config.playerMarketsEnabled) {
            return ValidationResult.fail("Les comptoirs marchands joueurs sont desactives.", "player markets disabled");
        }
        int owned = MerchantCounterRegistry.countActivePlayerMarkets(player.getUUID().toString());
        if (owned >= config.maxPlayerMarketsPerPlayer) {
            return ValidationResult.fail("Vous avez atteint la limite de comptoirs marchands.", "player market limit reached");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateStackForSale(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ValidationResult.fail("Aucun item a mettre en vente.", "empty stack");
        MarketConfig config = MarketConfig.load();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemId = id == null ? "minecraft:air" : id.toString();
        Set<String> blocked = normalize(config.blockedItemIds);
        if (blocked.contains(itemId)) {
            return ValidationResult.fail("Cet item ne peut pas etre vendu dans un Comptoir marchand.", "blocked item " + itemId);
        }
        if (config.allowedItemIds != null && !config.allowedItemIds.isEmpty()) {
            Set<String> allowed = normalize(config.allowedItemIds);
            if (!allowed.contains(itemId)) {
                return ValidationResult.fail("Cet item ne peut pas etre vendu dans un Comptoir marchand.", "item not allowlisted " + itemId);
            }
        }
        if (!config.allowNBTItemsInPlayerMarkets && stack.hasTag()) {
            return ValidationResult.fail("Cet item contient des donnees speciales et ne peut pas etre mis en vente.", "nbt disallowed");
        }
        if (!config.allowDamagedItemsInPlayerMarkets && stack.isDamaged()) {
            return ValidationResult.fail("Cet item contient des donnees speciales et ne peut pas etre mis en vente.", "damaged disallowed");
        }
        if (!config.allowEnchantedItemsInPlayerMarkets && stack.isEnchanted()) {
            return ValidationResult.fail("Cet item contient des donnees speciales et ne peut pas etre mis en vente.", "enchanted disallowed");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validatePrice(long price) {
        MarketConfig config = MarketConfig.load();
        if (price < config.minItemPriceInIron) {
            return ValidationResult.fail("Le prix doit etre superieur ou egal a 1 fer.", "price below minimum");
        }
        if (price > config.maxStackPriceInIron) {
            return ValidationResult.fail("Le prix depasse la limite autorisee.", "price above maximum");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateSaleConfiguration(MerchantCounterBlockEntity counter, int slot, ItemStack stack, long price) {
        if (!counter.isPlayerMarket()) return ValidationResult.fail("Action impossible.", "not a player market");
        ValidationResult stackValidation = validateStackForSale(stack);
        if (!stackValidation.allowed()) return stackValidation;
        ValidationResult priceValidation = validatePrice(price);
        if (!priceValidation.allowed()) return priceValidation;
        if (countItemsForSale(counter, slot) > MarketConfig.load().maxItemsPerPlayerMarket) {
            return ValidationResult.fail("Ce comptoir contient deja le nombre maximum d'items en vente.", "too many configured items");
        }
        return ValidationResult.ok();
    }

    public static int countItemsForSale(MerchantCounterBlockEntity counter, int configuredSlot) {
        int count = 0;
        for (int slot = 0; slot < MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS; slot++) {
            ItemStack current = counter.playerMarketStack(slot);
            if (current.isEmpty()) continue;
            if (slot == configuredSlot || counter.isPlayerSlotEnabled(slot) || counter.getPlayerSlotPrice(slot) > 0) count++;
        }
        return count;
    }

    public static void logViolation(ServerPlayer player, MerchantCounterBlockEntity counter, String reason) {
        MedievalCoin.LOGGER.warn("Securite comptoir joueur: joueur={} comptoir={} raison={}",
                player.getUUID(), counter.getCounterId(), reason);
    }

    private static Set<String> normalize(List<String> raw) {
        Set<String> result = new HashSet<>();
        for (String value : raw) {
            if (value == null) continue;
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public record ValidationResult(boolean allowed, String message, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "", "");
        }

        public static ValidationResult fail(String message, String reason) {
            return new ValidationResult(false, message, reason);
        }
    }
}
