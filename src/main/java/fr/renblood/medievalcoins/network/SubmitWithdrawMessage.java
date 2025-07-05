// src/main/java/fr/renblood/medievalcoins/network/SubmitWithdrawMessage.java
package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiMenu;
import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class SubmitWithdrawMessage {
    private final BlockPos pos;
    private final int coinType;
    private final int amount;

    public SubmitWithdrawMessage(BlockPos pos, int coinType, int amount) {
        this.pos      = pos.immutable();
        this.coinType = coinType;
        this.amount   = amount;
    }

    public static void encode(SubmitWithdrawMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.coinType);
        buf.writeInt(msg.amount);
    }

    public static SubmitWithdrawMessage decode(FriendlyByteBuf buf) {
        BlockPos pos   = buf.readBlockPos();
        int coinType   = buf.readInt();
        int amount     = buf.readInt();
        return new SubmitWithdrawMessage(pos, coinType, amount);
    }

    public static void handle(SubmitWithdrawMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // 1) On est bien dans le bon menu ?
            if (!(sender.containerMenu instanceof WithdrawGuiMenu)) return;

            // 2) Charge ou récupère le PlayerModel
            String uuid = sender.getGameProfile().getId().toString();
            PlayerModel pm = PlayerCache.getPlayer(uuid);
            if (pm == null) {
                try {
                    pm = ApiClient.getPlayer(uuid);
                } catch (Exception e) {
                    MedievalCoin.LOGGER.error("Erreur API getPlayer", e);
                    sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                    return;
                }
                PlayerCache.updatePlayer(pm);
            }

            // 3) Prépare l’ItemStack à donner
            ItemStack giveStack;
            switch (msg.coinType) {
                case 0 -> giveStack = new ItemStack(Coins.IRON_COIN.get(), msg.amount);
                case 1 -> giveStack = new ItemStack(Coins.BRONZE_COIN.get(), msg.amount);
                case 2 -> giveStack = new ItemStack(Coins.SILVER_COIN.get(), msg.amount);
                case 3 -> giveStack = new ItemStack(Coins.GOLD_COIN.get(), msg.amount);
                default -> giveStack = ItemStack.EMPTY;
            }
            if (giveStack.isEmpty()) {
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                return;
            }

            // 4) Vérifie de la place dans l’inventaire
            Inventory inv = sender.getInventory();
            if (!inv.add(giveStack.copy())) {
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_no_space"));
                return;
            }

            try {
                // 5) Appelle l’API pour débiter et récupérer le nouveau solde
                int newBalance = ApiClient.withdraw(pm.id_minecraft, msg.coinType, msg.amount);
                pm.money = newBalance;
                PlayerCache.updatePlayer(pm);

                // 6) Envoie la mise à jour du solde au client
                MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> sender),
                        new MoneyUpdateMessage(uuid, newBalance)
                );

                // 7) Confirmation en chat
                sender.sendSystemMessage(
                        Component.translatable("chat.medieval_coins.withdraw_success", msg.amount)
                );

                MedievalCoin.LOGGER.info(
                        "Withdraw {}×type{} for {} → new balance {}",
                        msg.amount, msg.coinType, uuid, newBalance
                );

            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Failed to withdraw for " + uuid, e);
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
            }
        });
        ctx.setPacketHandled(true);
    }
}
