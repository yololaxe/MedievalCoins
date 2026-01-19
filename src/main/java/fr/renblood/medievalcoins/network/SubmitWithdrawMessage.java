// src/main/java/fr/renblood/medievalcoins/network/SubmitWithdrawMessage.java
package fr.renblood.medievalcoins.network;

import com.mojang.authlib.GameProfile;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.inventory.banker.WithdrawGuiMenu;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.PlayerCache;
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
    }

    public static SubmitWithdrawMessage decode(FriendlyByteBuf buf) {
        return new SubmitWithdrawMessage(
                buf.readBlockPos(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(SubmitWithdrawMessage msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof WithdrawGuiMenu)) return;

            // Vérification du cooldown
            UUID uuid = player.getGameProfile().getId();
            long now = System.currentTimeMillis();
            if (lastWithdrawTime.containsKey(uuid)) {
                long last = lastWithdrawTime.get(uuid);
                if (now - last < COOLDOWN_MS) {
                    // Optionnel : envoyer un message de spam
                    // player.sendSystemMessage(Component.literal("⏳ Veuillez attendre 1 seconde entre chaque retrait."));
                    return;
                }
            }
            lastWithdrawTime.put(uuid, now);

            // --- 1) Prépare la pile à donner ---
            Item item = switch (msg.coinType) {
                case 0 -> Coins.IRON_COIN.get();
                case 1 -> Coins.BRONZE_COIN.get();
                case 2 -> Coins.SILVER_COIN.get();
                default -> Coins.GOLD_COIN.get();
            };
            ItemStack toGive = new ItemStack(item, msg.amount);
            if (toGive.isEmpty()) {
                player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                return;
            }

            // --- 2) Vérification de la place (simulate add sans modifier) ---
            Inventory inv = player.getInventory();
            if (!hasInventorySpace(inv, toGive)) {
                player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_no_space"));
                return;
            }

            // --- 3) Appel API et mise à jour du solde ---
            String uuidString = uuid.toString();
            PlayerModel pm = PlayerCache.getPlayer(uuidString);
            if (pm == null) {
                try {
                    pm = ApiClient.getPlayer(uuidString);
                    PlayerCache.updatePlayer(pm);
                } catch (Exception e) {
                    MedievalCoin.LOGGER.error("API getPlayer failed", e);
                    player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                    return;
                }
            }
            int newBalance;
            try {
                newBalance = ApiClient.withdraw(pm.id_minecraft, msg.coinType, msg.amount);
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("API withdraw failed", e);
                player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error"));
                return;
            }
            pm.money = newBalance;
            PlayerCache.updatePlayer(pm);

            // --- 4) Donne réellement la pile ---
            inv.add(toGive);

            // --- 5) Notifie le client du nouveau solde ---
            MedievalCoin.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new MoneyUpdateMessage(uuidString, newBalance)
            );

            // --- 6) Message de succès ---
            player.sendSystemMessage(
                    Component.translatable("chat.medieval_coins.withdraw_success", msg.amount)
            );
            MedievalCoin.LOGGER.info("Withdraw {}×type{} for {} → new balance {}",
                    msg.amount, msg.coinType, uuidString, newBalance
            );
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Simule l'ajout de {@code stack} dans l'inventaire {@code inv}
     * sans le modifier, et renvoie true si toute la pile peut rentrer.
     */
    private static boolean hasInventorySpace(Inventory inv, ItemStack stack) {
        int needed = stack.getCount();
        int maxStackSize = stack.getMaxStackSize();

        // Parcours de chaque slot
        for (int i = 0; i < inv.items.size() && needed > 0; i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) {
                // slot vide → peut accueillir un maxStackSize
                needed -= maxStackSize;
            } else if (ItemStack.isSameItem(s, stack)) {
                // même item → peut accueillir (max - actuel)
                needed -= (maxStackSize - s.getCount());
            }
        }
        return needed <= 0;
    }
}
