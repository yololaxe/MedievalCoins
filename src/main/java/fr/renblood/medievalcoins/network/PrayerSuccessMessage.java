package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.divine.DivineSessionManager;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PrayerSuccessMessage {
    private final int gameId;

    public PrayerSuccessMessage() {
        this(0);
    }

    public PrayerSuccessMessage(int gameId) {
        this.gameId = gameId;
    }

    public static void encode(PrayerSuccessMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.gameId);
    }

    public static PrayerSuccessMessage decode(FriendlyByteBuf buffer) {
        return new PrayerSuccessMessage(buffer.readVarInt());
    }

    public static void handle(PrayerSuccessMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null && DivineSessionManager.completePrayer(sender, message.gameId)) {
                TutorialManager.recordPrayerCompleted(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
