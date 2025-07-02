// src/main/java/fr/renblood/medievalcoins/network/SubmitWithdrawMessage.java
package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiMenu;
import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

import io.netty.buffer.Unpooled;

import static net.minecraft.world.item.Items.IRON_INGOT;

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

            // 1) Vérifier que le container est bien WithdrawGuiMenu
            if (!(sender.containerMenu instanceof WithdrawGuiMenu)) return;

            // 2) Charger ou récupérer le PlayerModel
            String mcUuid = sender.getGameProfile().getId().toString();
            PlayerModel pm = PlayerCache.getPlayer(mcUuid);
            if (pm == null) {
                try {
                    pm = ApiClient.getPlayer(mcUuid);
                } catch (Exception e) {
                    MedievalCoin.LOGGER.error("Erreur API getPlayer", e);
                    sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                    return;
                }
                PlayerCache.updatePlayer(pm);
            }

            // 3) Préparer l’ItemStack à donner
            ItemStack giveStack = switch (msg.coinType) {
                case 0 -> new ItemStack(IRON_INGOT, msg.amount);
                case 1 -> new ItemStack(Coins.BRONZE_COIN.get(), msg.amount);
                case 2 -> new ItemStack(Coins.SILVER_COIN.get(), msg.amount);
                case 3 -> new ItemStack(Coins.GOLD_COIN.get(), msg.amount);
                default -> ItemStack.EMPTY;
            };
            if (giveStack.isEmpty()) {
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                return;
            }

            // 4) Vérifier place dans l’inventaire
            Inventory inv = sender.getInventory();
            if (!inv.add(giveStack.copy())) {
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_no_space"));
                return;
            }

            try {
                // 5) Appel API pour débiter et récupérer le nouveau solde
                int newBalance = ApiClient.withdraw(pm.id_minecraft, msg.coinType, msg.amount);
                pm.money = newBalance;
                PlayerCache.updatePlayer(pm);

                // 6) Envoyer la mise à jour du solde au client
                MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> sender),
                        new MoneyUpdateMessage(mcUuid, newBalance)
                );

                // 7) Réouvrir le GUI banquier à la même position
                BlockPos reopenPos = msg.pos;
                NetworkHooks.openScreen(
                        sender,
                        new SimpleMenuProvider(
                                (windowId, playerInv, pl) -> {
                                    FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
                                    extra.writeBlockPos(reopenPos);
                                    return new WithdrawGuiMenu(windowId, playerInv, reopenPos);
                                },
                                Component.translatable("screen.medieval_coins.banker")
                        ),
                        reopenPos
                );

                // 8) Confirmation en chat
                sender.sendSystemMessage(
                        Component.translatable("chat.medieval_coins.withdraw_success", msg.amount)
                );

                MedievalCoin.LOGGER.info(
                        "Withdraw {} of type {} for {} succeeded; new balance = {}",
                        msg.amount, msg.coinType, mcUuid, newBalance
                );
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("Failed to withdraw for " + mcUuid, e);
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
            }
        });
        ctx.setPacketHandled(true);
    }
}
