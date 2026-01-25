package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class InventoryLimitHandler {

    // --- PARTIE SERVEUR ---

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int limit = getInventoryLimit(player);
        if (limit >= 36) return;

        if (!canFitInAllowedSlots(player.getInventory(), event.getItem().getItem(), limit)) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    private static boolean canFitInAllowedSlots(Inventory inv, ItemStack stack, int limit) {
        int remaining = stack.getCount();
        for (int i = 0; i < limit; i++) {
            ItemStack slotStack = inv.getItem(i);
            if (!slotStack.isEmpty() && ItemStack.isSameItemSameTags(slotStack, stack) && slotStack.isStackable()) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                if (space > 0) {
                    remaining -= space;
                    if (remaining <= 0) return true;
                }
            }
        }
        for (int i = 0; i < limit; i++) {
            if (inv.getItem(i).isEmpty()) {
                remaining -= stack.getMaxStackSize();
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        int limit = getInventoryLimit(player);
        Inventory inv = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (i >= limit) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    player.drop(stack.copy(), true);
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private static int getInventoryLimit(Player player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null) {
            return Math.max(1, Math.min(36, pm.place));
        }
        return 36;
    }
}
