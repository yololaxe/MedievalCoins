package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.market.counter.MarketTransactionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record MerchantCounterHistoryResponseMessage(List<MarketTransactionData> transactions, boolean backendUnavailable) {
    public static void encode(MerchantCounterHistoryResponseMessage message, FriendlyByteBuf buffer) {
        buffer.writeCollection(message.transactions, (target, transaction) -> transaction.write(target));
        buffer.writeBoolean(message.backendUnavailable);
    }

    public static MerchantCounterHistoryResponseMessage decode(FriendlyByteBuf buffer) {
        return new MerchantCounterHistoryResponseMessage(buffer.readList(MarketTransactionData::read), buffer.readBoolean());
    }

    public static void handle(MerchantCounterHistoryResponseMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleMerchantCounterHistory(message)));
        context.setPacketHandled(true);
    }
}
