// src/main/java/fr/renblood/medievalcoins/procedures/OpenWithdrawGuiMessage.java
package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenWithdrawGuiMessage {
    private final int x, y, z;

    public OpenWithdrawGuiMessage(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(OpenWithdrawGuiMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x);
        buf.writeInt(msg.y);
        buf.writeInt(msg.z);
    }

    public static OpenWithdrawGuiMessage decode(FriendlyByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        return new OpenWithdrawGuiMessage(x, y, z);
    }

    public static void handle(OpenWithdrawGuiMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            BlockPos pos = new BlockPos(msg.x, msg.y, msg.z);

            MenuProvider provider = new SimpleMenuProvider(
                    (windowId, playerInv, dataBuf) -> {
                        // write the same BlockPos into the dataBuf so client can read it

                        return new WithdrawGuiMenu(windowId, playerInv, pos);
                    },
                    Component.empty() // or Component.empty() //.translatable("screen.medieval_coins.withdraw")
            );

            NetworkHooks.openScreen(
                    sender,
                    provider,
                    buf -> buf.writeBlockPos(pos)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
