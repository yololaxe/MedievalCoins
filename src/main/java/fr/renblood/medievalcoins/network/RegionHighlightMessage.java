package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.client.renderer.RegionRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RegionHighlightMessage {
    private final BlockPos min;
    private final BlockPos max;
    private final int color; // ARGB
    private final int durationTicks;

    public RegionHighlightMessage(BlockPos min, BlockPos max, int color, int durationTicks) {
        this.min = min;
        this.max = max;
        this.color = color;
        this.durationTicks = durationTicks;
    }

    public static void encode(RegionHighlightMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.min);
        buf.writeBlockPos(msg.max);
        buf.writeInt(msg.color);
        buf.writeInt(msg.durationTicks);
    }

    public static RegionHighlightMessage decode(FriendlyByteBuf buf) {
        return new RegionHighlightMessage(buf.readBlockPos(), buf.readBlockPos(), buf.readInt(), buf.readInt());
    }

    public static void handle(RegionHighlightMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Exécuter uniquement sur le client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                RegionRenderer.addHighlight(msg.min, msg.max, msg.color, msg.durationTicks)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
