package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.api.model.PlayerModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerStatsUpdateMessage {
    private final PlayerModel playerModel;

    public PlayerStatsUpdateMessage(PlayerModel playerModel) {
        this.playerModel = playerModel;
    }

    public PlayerModel getPlayerModel() {
        return playerModel;
    }

    public static void encode(PlayerStatsUpdateMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(PlayerModel.GSON.toJson(msg.playerModel));
    }

    public static PlayerStatsUpdateMessage decode(FriendlyByteBuf buf) {
        return new PlayerStatsUpdateMessage(PlayerModel.fromJson(buf.readUtf()));
    }

    public static void handle(PlayerStatsUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePlayerStatsUpdate(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}