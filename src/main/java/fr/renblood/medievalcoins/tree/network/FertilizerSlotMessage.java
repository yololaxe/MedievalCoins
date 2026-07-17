package fr.renblood.medievalcoins.tree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import fr.renblood.medievalcoins.network.ClientPacketHandler;

import java.util.function.Supplier;

public class FertilizerSlotMessage {
    private final ItemStack stack;

    public FertilizerSlotMessage(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    public static void encode(FertilizerSlotMessage msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static FertilizerSlotMessage decode(FriendlyByteBuf buf) {
        return new FertilizerSlotMessage(buf.readItem());
    }

    public static void handle(FertilizerSlotMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleFertilizerSlot(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}