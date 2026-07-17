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
            if (msg.coinType < 0 || msg.coinType > 3 || msg.amount < 1 || msg.amount > 64) {
                player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_invalid"));
                return;
            }

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
            // Calcul du coût total en unité de base (Fer)
            // 0=Fer(1), 1=Bronze(64), 2=Argent(4096), 3=Or(262144)
            long unitCost = switch (msg.coinType) {
                case 0 -> 1L;
                case 1 -> 64L;
                case 2 -> 64L * 64L;
                case 3 -> 64L * 64L * 64L;
                default -> 0L;
            };
            long totalCostLong = unitCost * msg.amount;
            int totalCost = (int) totalCostLong; // Attention overflow si montant énorme

            ApiExecutor.execute(() -> {
            try {
                PlayerModel model = PlayerCache.getPlayer(uuidString);
                if (model == null) model = ApiClient.getPlayer(uuidString);
                if (model.money < totalCost) {
                    player.getServer().execute(() ->
                            player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_insufficient")));
                    return;
                }
                int newBalance = ApiClient.withdraw(model.id_minecraft, 0, totalCost);
                PlayerModel updatedModel = model;
                player.getServer().execute(() -> {
                    updatedModel.money = newBalance;
                    PlayerCache.updatePlayer(updatedModel);
                    if (!player.getInventory().add(toGive)) player.drop(toGive, false);
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                            new MoneyUpdateMessage(uuidString, newBalance));
                    MedievalCoin.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player),
                            new PlayerStatsUpdateMessage(updatedModel));
                    player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_success", msg.amount));
                    TutorialManager.recordBankWithdraw(player);
                });
                MedievalCoin.LOGGER.info("Withdraw {}x type{} (cost {}) for {} -> new balance {}",
                        msg.amount, msg.coinType, totalCost, uuidString, newBalance);
            } catch (Exception e) {
                MedievalCoin.LOGGER.error("API withdraw failed", e);
                player.getServer().execute(() ->
                        player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_error")));
            }
            });
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
