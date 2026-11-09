package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class MarketNotificationService {
    private MarketNotificationService() {
    }

    public static void notifyExhausted(MinecraftServer server, MerchantCounterBlockEntity counter, String itemName) {
        Component message = Component.literal("Stock epuise: " + itemName + " au comptoir " + counter.getCounterId() + ".")
                .withStyle(ChatFormatting.YELLOW);
        boolean ownerNotified = false;
        try {
            if (counter.getOwnerId() != null && !counter.getOwnerId().isBlank()) {
                ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(counter.getOwnerId()));
                if (owner != null) {
                    owner.sendSystemMessage(message);
                    ownerNotified = true;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        if (!ownerNotified) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (MarketPermissions.isAdmin(player)) player.sendSystemMessage(message);
            }
        }
    }
}
