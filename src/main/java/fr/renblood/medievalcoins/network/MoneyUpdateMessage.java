package fr.renblood.medievalcoins.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MoneyUpdateMessage {
    public final String mcId;
    public final long newMoney;

    public MoneyUpdateMessage(String mcId, long newMoney) {
        this.mcId     = mcId;
        this.newMoney = newMoney;
    }

    public static void encode(MoneyUpdateMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.mcId, 255);
        buf.writeLong(msg.newMoney);
    }

    public static MoneyUpdateMessage decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(255);
        long m  = buf.readLong();
        return new MoneyUpdateMessage(id, m);
    }

    public static void handle(MoneyUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleMoneyUpdate(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}