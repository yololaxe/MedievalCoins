package fr.renblood.medievalcoins.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenPrayerScreenMessage {
    private final int gameId;
    private final String gameName;

    public OpenPrayerScreenMessage() {
        this(0, "Rune Hunt");
    }

    public OpenPrayerScreenMessage(int gameId, String gameName) {
        this.gameId = gameId;
        this.gameName = gameName == null ? "Rune Hunt" : gameName;
    }

    public int gameId() { return gameId; }
    public String gameName() { return gameName; }

    public static void encode(OpenPrayerScreenMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.gameId);
        buffer.writeUtf(message.gameName);
    }

    public static OpenPrayerScreenMessage decode(FriendlyByteBuf buffer) {
        return new OpenPrayerScreenMessage(buffer.readVarInt(), buffer.readUtf(64));
    }

    public static void handle(OpenPrayerScreenMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleOpenPrayerScreen(message));
        });
        context.setPacketHandled(true);
    }
}