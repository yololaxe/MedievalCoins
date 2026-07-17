package fr.renblood.medievalcoins.mixin;

import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow public abstract int getContainerSlot();

    @Inject(method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void onMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (isLocked()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    private void onMayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (isLocked()) {
            cir.setReturnValue(false);
        }
    }

    private boolean isLocked() {
        Slot slot = (Slot) (Object) this;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory inventory) {
            int index = this.getContainerSlot();
            if (index >= 0 && index < 36) {
                int limit = getInventoryLimit(inventory.player);
                return index >= limit;
            }
        }
        return false;
    }

    private int getInventoryLimit(Player player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel model = PlayerCache.getPlayer(uuid);
        if (model != null) {
            return Math.max(1, Math.min(36, model.place));
        }
        return 36;
    }
}
