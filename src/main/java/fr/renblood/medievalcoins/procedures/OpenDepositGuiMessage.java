// src/main/java/fr/renblood/medievalcoins/procedures/OpenDepositGuiMessage.java
package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.inventory.banker.DepositGuiMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenDepositGuiMessage {
    private final int x, y, z;

    public OpenDepositGuiMessage(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(OpenDepositGuiMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x);
        buf.writeInt(msg.y);
        buf.writeInt(msg.z);
    }

    public static OpenDepositGuiMessage decode(FriendlyByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        return new OpenDepositGuiMessage(x, y, z);
    }

    public static void handle(OpenDepositGuiMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // On récupère la position passée depuis le client
            BlockPos pos = new BlockPos(msg.x, msg.y, msg.z);

            // 1) Création du provider qui instancie le menu côté serveur
            MenuProvider provider = new SimpleMenuProvider(
                    // le factory doit passer les 4 arguments à ton DepositGuiMenu
                    (windowId, playerInv, dataBuf) ->
                            new DepositGuiMenu(windowId, playerInv, pos, windowId),
                    // titre de la fenêtre
                    Component.empty()     //Component.translatable("gui.medieval_coins.banker_gui.deposit_title")
            );

            // 2) Ouverture du GUI côté serveur :
            //    openScreen est la méthode dispo en 1.20.1
            NetworkHooks.openScreen(
                    sender,
                    provider,
                    // on réécrit les mêmes données dans le buffer pour le client
                    extraData -> extraData.writeBlockPos(pos)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
