package fr.renblood.medievalcoins.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenQuestScreenMessage {
    public static void encode(OpenQuestScreenMessage msg, FriendlyByteBuf buf) {
    }

    public static OpenQuestScreenMessage decode(FriendlyByteBuf buf) {
        return new OpenQuestScreenMessage();
    }

    public static void handle(OpenQuestScreenMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenQuestScreen(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}