package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.inventory.banker.DepositGuiMenu;
import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class DepositAllCoinsMessage {
    private java.util.UUID operationId = java.util.UUID.randomUUID();
    public static void encode(DepositAllCoinsMessage message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.operationId);
    }

    public static DepositAllCoinsMessage decode(FriendlyByteBuf buffer) {
        DepositAllCoinsMessage result = new DepositAllCoinsMessage(); result.operationId = buffer.readUUID(); return result;
    }

    public static void handle(DepositAllCoinsMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (sender.containerMenu instanceof DepositGuiMenu) fr.renblood.medievalcoins.bank.BankService.deposit(sender, true, msg.operationId);
        });
        ctx.setPacketHandled(true);
    }
}
