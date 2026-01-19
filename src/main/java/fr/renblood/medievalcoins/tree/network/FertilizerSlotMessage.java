package fr.renblood.medievalcoins.tree.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.capability.FertilizerCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FertilizerSlotMessage {
    private final ItemStack stack;

    public FertilizerSlotMessage(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(FertilizerSlotMessage msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static FertilizerSlotMessage decode(FriendlyByteBuf buf) {
        return new FertilizerSlotMessage(buf.readItem());
    }

    public static void handle(FertilizerSlotMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (MedievalCoin.DEBUG_MODE) {
                MedievalCoin.LOGGER.info("Client received FertilizerSlotMessage. Stack: " + msg.stack + " Count: " + msg.stack.getCount());
            }

            mc.player.getCapability(FertilizerCapabilityHandler.FERTILIZER_CAP).ifPresent(inv -> {
                inv.setStackInSlot(0, msg.stack);
                if (MedievalCoin.DEBUG_MODE) {
                    MedievalCoin.LOGGER.info("Client capability updated. New slot content: " + inv.getStackInSlot(0));
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
