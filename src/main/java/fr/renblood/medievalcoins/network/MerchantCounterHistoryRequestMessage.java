package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterAdminMenu;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterOwnerMenu;
import fr.renblood.medievalcoins.market.counter.MarketTransactionData;
import fr.renblood.medievalcoins.market.service.MarketApiService;
import fr.renblood.medievalcoins.market.service.MarketPermissions;
import fr.renblood.medievalcoins.market.service.PendingMarketTransactionManager;
import fr.renblood.medievalcoins.market.service.MarketTransactionHistoryCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record MerchantCounterHistoryRequestMessage(BlockPos pos) {
    public static void encode(MerchantCounterHistoryRequestMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
    }

    public static MerchantCounterHistoryRequestMessage decode(FriendlyByteBuf buffer) {
        return new MerchantCounterHistoryRequestMessage(buffer.readBlockPos());
    }

    public static void handle(MerchantCounterHistoryRequestMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.distanceToSqr(message.pos.getX() + .5, message.pos.getY() + .5, message.pos.getZ() + .5) > 64) return;
            if (!(player.level().getBlockEntity(message.pos) instanceof MerchantCounterBlockEntity counter)) return;
            boolean adminMenu = player.containerMenu instanceof MerchantCounterAdminMenu && MarketPermissions.isAdmin(player);
            boolean ownerMenu = player.containerMenu instanceof MerchantCounterOwnerMenu && counter.isOwner(player.getUUID());
            if (!adminMenu && !ownerMenu) return;

            List<MarketTransactionData> pending = PendingMarketTransactionManager.entriesForCounter(counter.getCounterId())
                    .stream().map(value -> MarketTransactionData.from(value, true)).toList();
            MarketApiService.fetchCounterTransactions(counter.getCounterId()).whenComplete((backend, failure) ->
                    player.getServer().execute(() -> {
                        List<MarketTransactionData> result = new ArrayList<>();
                        result.addAll(pending);
                        if (backend != null) {
                            MarketTransactionHistoryCache.remember(counter.getCounterId(), backend);
                            backend.stream().limit(50).map(value -> MarketTransactionData.from(value, false)).forEach(result::add);
                        }
                        MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                                new MerchantCounterHistoryResponseMessage(result.stream().limit(50).toList(), failure != null));
                    }));
        });
        context.setPacketHandled(true);
    }
}
