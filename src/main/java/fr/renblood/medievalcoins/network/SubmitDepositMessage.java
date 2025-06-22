// src/main/java/fr/renblood/medievalcoins/network/SubmitDepositMessage.java
package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class SubmitDepositMessage {
    private final int amount;

    public SubmitDepositMessage(int amount) {
        this.amount = amount;
    }

    // sérialisation
    public static void encode(SubmitDepositMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.amount);
    }

    public static SubmitDepositMessage decode(FriendlyByteBuf buf) {
        return new SubmitDepositMessage(buf.readInt());
    }

    public static void handle(SubmitDepositMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            String mcId = sender.getGameProfile().getName();  // correctement c'est id_minecraft
            try {
                // effectue le dépôt et récupère le nouveau solde brut
                int newBalance = ApiClient.deposit(mcId, msg.amount);

                // met à jour le cache local
                PlayerCache.updatePlayer( ApiClient.getPlayer(mcId) );

                // notifie le client pour rafraîchir l’affichage
                MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> sender),
                        new MoneyUpdateMessage(mcId, newBalance)
                );
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Erreur dépôt API pour " + mcId, e);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
