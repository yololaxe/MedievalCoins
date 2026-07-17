package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class BankerGuiRefreshMessage {
    public BankerGuiRefreshMessage() {}

    public static void encode(BankerGuiRefreshMessage msg, FriendlyByteBuf buf) {
        // pas de données à sérialiser
    }

    public static BankerGuiRefreshMessage decode(FriendlyByteBuf buf) {
        // pas de données à lire
        return new BankerGuiRefreshMessage();
    }

    public static void handle(BankerGuiRefreshMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            String mcId = sender.getStringUUID();
            ApiExecutor.execute(() -> {
            try {
                PlayerModel pm = ApiClient.getPlayer(mcId);
                sender.getServer().execute(() -> {
                    PlayerCache.updatePlayer(pm);
                    MedievalCoin.PACKET_HANDLER.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new MoneyUpdateMessage(pm.id_minecraft, pm.money)
                    );
                });
            } catch (Exception e) {
                MedievalCoin.LOGGER.warn("Refresh API failed for UUID=" + mcId, e);
            }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
