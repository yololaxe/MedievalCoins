package fr.renblood.medievalcoins.network;


import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import fr.renblood.medievalcoins.inventory.banker.DepositGuiMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkHooks;
import io.netty.buffer.Unpooled;


public class SubmitDepositMessage {
    private java.util.UUID operationId = java.util.UUID.randomUUID();
    private final BlockPos pos;
    private final ItemStack iron, bronze, silver, gold;

    public SubmitDepositMessage(BlockPos pos,
                                ItemStack iron,
                                ItemStack bronze,
                                ItemStack silver,
                                ItemStack gold) {
        this.pos    = pos.immutable();
        this.iron   = iron.copy();
        this.bronze = bronze.copy();
        this.silver = silver.copy();
        this.gold   = gold.copy();
    }

    public static void encode(SubmitDepositMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeItem(msg.iron);
        buf.writeItem(msg.bronze);
        buf.writeItem(msg.silver);
        buf.writeItem(msg.gold);
        buf.writeUUID(msg.operationId);
    }

    public static SubmitDepositMessage decode(FriendlyByteBuf buf) {
        BlockPos pos      = buf.readBlockPos();
        ItemStack iron    = buf.readItem();
        ItemStack bronze  = buf.readItem();
        ItemStack silver  = buf.readItem();
        ItemStack gold    = buf.readItem();
        SubmitDepositMessage result = new SubmitDepositMessage(pos, iron, bronze, silver, gold);
        result.operationId = buf.readUUID(); return result;
    }

    public static void handle(SubmitDepositMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (sender.containerMenu instanceof DepositGuiMenu) fr.renblood.medievalcoins.bank.BankService.deposit(sender, false, msg.operationId);
        });
        ctx.setPacketHandled(true);
    }
}
