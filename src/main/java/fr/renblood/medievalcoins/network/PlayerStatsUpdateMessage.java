package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerStatsUpdateMessage {
    private final PlayerModel playerModel;

    public PlayerStatsUpdateMessage(PlayerModel playerModel) {
        this.playerModel = playerModel;
    }

    public static void encode(PlayerStatsUpdateMessage msg, FriendlyByteBuf buf) {
        // On sérialise le PlayerModel en JSON string et on l'écrit dans le buffer
        buf.writeUtf(PlayerModel.GSON.toJson(msg.playerModel));
    }

    public static PlayerStatsUpdateMessage decode(FriendlyByteBuf buf) {
        // On lit le JSON string et on le désérialise en PlayerModel
        return new PlayerStatsUpdateMessage(PlayerModel.fromJson(buf.readUtf()));
    }

    public static void handle(PlayerStatsUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Met à jour le cache client avec le PlayerModel reçu
            PlayerCache.updatePlayer(msg.playerModel);
        });
        ctx.get().setPacketHandled(true);
    }
}
