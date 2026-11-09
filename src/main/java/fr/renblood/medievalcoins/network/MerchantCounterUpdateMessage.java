package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import fr.renblood.medievalcoins.market.service.MarketPermissions;

import java.util.function.Supplier;

public record MerchantCounterUpdateMessage(BlockPos pos, boolean adminUpdate, MerchantCounterData data) {
    public static void encode(MerchantCounterUpdateMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeBoolean(message.adminUpdate);
        message.data.write(buffer);
    }

    public static MerchantCounterUpdateMessage decode(FriendlyByteBuf buffer) {
        return new MerchantCounterUpdateMessage(buffer.readBlockPos(), buffer.readBoolean(), MerchantCounterData.read(buffer));
    }

    public static void handle(MerchantCounterUpdateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.distanceToSqr(message.pos.getX() + .5, message.pos.getY() + .5, message.pos.getZ() + .5) > 64) return;
            if (!(player.level().getBlockEntity(message.pos) instanceof MerchantCounterBlockEntity counter)) return;

            if (message.adminUpdate && MarketPermissions.isAdmin(player)) {
                counter.touchOwnerInteraction();
                counter.applyAdminUpdate(message.data);
            } else if (!message.adminUpdate && counter.isOwner(player.getUUID())) {
                counter.touchOwnerInteraction();
                counter.applyOwnerUpdate(message.data.name(), message.data.active(), message.data.sellModeEnabled(),
                        message.data.cityId(), message.data.cityName());
            }
        });
        context.setPacketHandled(true);
    }
}
