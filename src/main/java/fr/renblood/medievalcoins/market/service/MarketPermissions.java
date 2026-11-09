package fr.renblood.medievalcoins.market.service;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class MarketPermissions {
    private MarketPermissions() {
    }

    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(2) || player.getServer() != null
                && player.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean isAdmin(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        ServerPlayer player = source.getPlayer();
        return player != null && isAdmin(player);
    }
}
