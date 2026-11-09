package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterMenus;
import fr.renblood.medievalcoins.market.counter.MerchantCounterOwnerMenu;
import fr.renblood.medievalcoins.market.service.PlayerMarketSecurityService;
import fr.renblood.medievalcoins.market.service.MarketCommerceService;
import fr.renblood.medievalcoins.bank.EconomyFeedback;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerMarketOwnerActionMessage(BlockPos pos, String action, int slot, long value, boolean enabled) {
    public static void encode(PlayerMarketOwnerActionMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeUtf(message.action);
        buffer.writeVarInt(message.slot);
        buffer.writeLong(message.value);
        buffer.writeBoolean(message.enabled);
    }

    public static PlayerMarketOwnerActionMessage decode(FriendlyByteBuf buffer) {
        return new PlayerMarketOwnerActionMessage(buffer.readBlockPos(), buffer.readUtf(), buffer.readVarInt(),
                buffer.readLong(), buffer.readBoolean());
    }

    public static void handle(PlayerMarketOwnerActionMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof MerchantCounterOwnerMenu menu)
                    || !menu.getPos().equals(message.pos) || !menu.stillValid(player)
                    || !(player.level().getBlockEntity(message.pos) instanceof MerchantCounterBlockEntity counter)
                    || !counter.isOwner(player.getUUID()) || (!counter.isPlayerMarket() && !"CLAIM".equals(message.action))) return;

            counter.touchOwnerInteraction();
            switch (message.action) {
                case "DEPOSIT_BATCH" -> MarketCommerceService.depositBatch(player, counter, message.value);
                case "DEPOSIT" -> MarketCommerceService.stock(player, counter, message.slot, true);
                case "WITHDRAW" -> MarketCommerceService.stock(player, counter, message.slot, false);
                case "CLAIM" -> counter.claimOwnerBalance(player);
                case "CONFIGURE", "CONFIGURE_TYPE" -> configure(player, counter, message);
                default -> { }
            }
        });
        context.setPacketHandled(true);
    }
    private static void configure(ServerPlayer player, MerchantCounterBlockEntity counter,
                                  PlayerMarketOwnerActionMessage message) {
        if (message.slot < 0 || message.slot >= MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS) return;
        if (counter.isTransactionInProgress()) {
            EconomyFeedback.send(player, "PENDING", "Attends la fin du transfert avant de modifier les prix.", "");
            return;
        }
        var validation = PlayerMarketSecurityService.validateSaleConfiguration(counter, message.slot,
                counter.playerMarketStack(message.slot), message.value);
        if (!validation.allowed()) {
            EconomyFeedback.send(player, "ERROR", validation.message(), "");
            PlayerMarketSecurityService.logViolation(player, counter, validation.reason());
            return;
        }
        if ("CONFIGURE".equals(message.action)) {
            counter.configurePlayerSlot(message.slot, message.value, message.enabled);
        } else if (!counter.configurePlayerItemType(message.slot, message.value, message.enabled)) {
            EconomyFeedback.send(player, "ERROR", "Le stock sélectionné a changé. Rouvre l’onglet Prix.", "");
            return;
        }
        TutorialManager.recordCounterConfigured(player, message.pos);
        MerchantCounterMenus.open(player, counter, true);
    }

}
