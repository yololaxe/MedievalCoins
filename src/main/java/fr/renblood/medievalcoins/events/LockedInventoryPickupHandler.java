package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public final class LockedInventoryPickupHandler {
    private LockedInventoryPickupHandler() {
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack pickedUp = event.getItem().getItem();
        if (availableCapacity(player, pickedUp) < pickedUp.getCount()) {
            event.setCanceled(true);
        }
    }

    private static int availableCapacity(ServerPlayer player, ItemStack pickedUp) {
        Inventory inventory = player.getInventory();
        int limit = getInventoryLimit(player);
        int capacity = 0;

        for (int slot = 0; slot < limit; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                capacity += pickedUp.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(existing, pickedUp)) {
                capacity += Math.max(0, Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize()) - existing.getCount());
            }
            if (capacity >= pickedUp.getCount()) return capacity;
        }
        return capacity;
    }

    private static int getInventoryLimit(ServerPlayer player) {
        PlayerModel model = PlayerCache.getPlayer(player.getStringUUID());
        return model == null ? 36 : Math.max(1, Math.min(36, model.place));
    }
}
