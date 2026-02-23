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
    }

    public static SubmitDepositMessage decode(FriendlyByteBuf buf) {
        BlockPos pos      = buf.readBlockPos();
        ItemStack iron    = buf.readItem();
        ItemStack bronze  = buf.readItem();
        ItemStack silver  = buf.readItem();
        ItemStack gold    = buf.readItem();
        return new SubmitDepositMessage(pos, iron, bronze, silver, gold);
    }

    public static void handle(SubmitDepositMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // 1) Récupérer le container actuel
            if (!(sender.containerMenu instanceof DepositGuiMenu depositMenu)) return;
            SimpleContainer inv = depositMenu.getDepositInv();

            // 2) Lire les quantités
            int ironCount   = inv.getItem(0).getCount();
            int bronzeCount = inv.getItem(1).getCount();
            int silverCount = inv.getItem(2).getCount();
            int goldCount   = inv.getItem(3).getCount();

            // Vérification si le dépôt est vide
            if (ironCount == 0 && bronzeCount == 0 && silverCount == 0 && goldCount == 0) {
                sender.sendSystemMessage(Component.translatable("chat.medieval_coins.deposit_empty"));
                return;
            }

            // 3) Conversion en "fer" (unité de base)
            // 64 Fer = 1 Bronze
            // 64 Bronze = 1 Argent
            // 64 Argent = 1 Or
            final long PER_IRON   = 1;
            final long PER_BRONZE = 64 * PER_IRON;
            final long PER_SILVER = 64 * PER_BRONZE;
            final long PER_GOLD   = 64 * PER_SILVER;
            
            long totalValueLong = ironCount * PER_IRON
                    + bronzeCount * PER_BRONZE
                    + silverCount * PER_SILVER
                    + goldCount * PER_GOLD;
            
            // On cast en int car l'API semble utiliser des int, attention aux dépassements si les montants sont énormes
            int totalValue = (int) totalValueLong;

            // 4) Vider les slots de dépôt
            for (int i = 0; i < 4; i++) {
                inv.setItem(i, ItemStack.EMPTY);
            }

            try {
                // 5) Charger ou récupérer le PlayerModel
                String mcUuid = sender.getGameProfile().getId().toString();
                PlayerModel pm = PlayerCache.getPlayer(mcUuid);
                if (pm == null) {
                    pm = ApiClient.getPlayer(mcUuid);
                    PlayerCache.updatePlayer(pm);
                }

                // 6) Appel API pour déposer et récupérer le nouveau solde
                int newBalance = ApiClient.deposit(pm.id_minecraft, totalValue);
                pm.money        = newBalance;
                PlayerCache.updatePlayer(pm);

                // 7) Envoyer la mise à jour au client (MoneyUpdateMessage est spécifique à l'argent, on garde pour compatibilité)
                MoneyUpdateMessage update = new MoneyUpdateMessage(mcUuid, newBalance);
                MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> sender),
                        update
                );
                
                // 7b) Envoyer le PlayerModel complet pour mettre à jour le cache client global
                MedievalCoin.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> sender),
                        new PlayerStatsUpdateMessage(pm)
                );

                // 8) Ré-ouvrir le GUI du banquier à la même position
                BlockPos reopenPos = depositMenu.getPos();

                NetworkHooks.openScreen(
                        sender,
                        new MenuProvider() {
                            @Override
                            public Component getDisplayName() {
                                return Component.translatable("screen.medievalcoins.banker");
                            }

                            @Override
                            public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
                                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                                buf.writeBlockPos(reopenPos);
                                return new BankerGuiMenu(windowId, inventory, buf);
                            }
                        },
                        buf -> buf.writeBlockPos(reopenPos)
                );

                // 9) Confirmation en chat
                sender.sendSystemMessage(
                        Component.translatable("chat.medieval_coins.deposit_success", totalValue)
                );

                MedievalCoin.LOGGER.info(
                        "Deposit of {} value for {} succeeded, new balance = {}",
                        totalValue, mcUuid, newBalance
                );
            } catch (Exception e) {
                MedievalCoin.LOGGER.error(
                        "Failed to deposit for " + sender.getGameProfile().getId(),
                        e
                );
            }
        });
        ctx.setPacketHandled(true);
    }
}