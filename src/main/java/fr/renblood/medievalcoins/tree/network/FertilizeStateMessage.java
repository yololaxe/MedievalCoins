package fr.renblood.medievalcoins.tree.network;

import fr.renblood.medievalcoins.tree.fertilize.FertilizeCommand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FertilizeStateMessage {
    private final boolean active;

    public FertilizeStateMessage(boolean active) {
        this.active = active;
    }

    public static void encode(FertilizeStateMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static FertilizeStateMessage decode(FriendlyByteBuf buf) {
        return new FertilizeStateMessage(buf.readBoolean());
    }

    public static void handle(FertilizeStateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Update client-side state
            FertilizeCommand.fertilizeActiveHUD = msg.active;
        });
        ctx.get().setPacketHandled(true);
    }
}
