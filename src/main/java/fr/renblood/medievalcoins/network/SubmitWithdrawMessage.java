// src/main/java/fr/renblood/medievalcoins/network/SubmitWithdrawMessage.java
package fr.renblood.medievalcoins.network;

import com.mojang.authlib.GameProfile;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiMenu;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SubmitWithdrawMessage {
    private UUID operationId = UUID.randomUUID();
    private final BlockPos pos;
    private final int coinType;
    private final int amount;
    
    private static final Map<UUID, Long> lastWithdrawTime = new HashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1 seconde

    public SubmitWithdrawMessage(BlockPos pos, int coinType, int amount) {
        this.pos      = pos.immutable();
        this.coinType = coinType;
        this.amount   = amount;
    }

    public static void encode(SubmitWithdrawMessage m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeInt(m.coinType);
        buf.writeInt(m.amount);
        buf.writeUUID(m.operationId);
    }

    public static SubmitWithdrawMessage decode(FriendlyByteBuf buf) {
        SubmitWithdrawMessage result = new SubmitWithdrawMessage(buf.readBlockPos(), buf.readInt(), buf.readInt());
        result.operationId = buf.readUUID(); return result;
    }

    public static void handle(SubmitWithdrawMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (sender.containerMenu instanceof WithdrawGuiMenu menu && menu.pos.equals(msg.pos)) fr.renblood.medievalcoins.bank.BankService.withdraw(sender, msg.coinType, msg.amount, msg.operationId);
        });
        ctx.setPacketHandled(true);
    }
}
