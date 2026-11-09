package fr.renblood.medievalcoins.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenNpcDialogueMessage(String npcName, String text, String texture) {
    public static void encode(OpenNpcDialogueMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.npcName);
        buffer.writeUtf(message.text);
        buffer.writeUtf(message.texture);
    }

    public static OpenNpcDialogueMessage decode(FriendlyByteBuf buffer) {
        return new OpenNpcDialogueMessage(buffer.readUtf(), buffer.readUtf(32767), buffer.readUtf());
    }

    public static void handle(OpenNpcDialogueMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenNpcDialogue(message));
        });
        context.setPacketHandled(true);
    }
}