package fr.renblood.medievalcoins.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.market.counter.MerchantCounterOwnerScreen;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
    private static final int PLAYER_INVENTORY_SIZE = 36;
    private static final int SLOT_SIZE = 16;
    private static final ResourceLocation LOCK_TEXTURE = new ResourceLocation(MedievalCoin.MODID, "textures/gui/lock.png");

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected T menu;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof MerchantCounterOwnerScreen screen && !screen.medievalCoins$showInventorySlots()) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int limit = getInventoryLimit(player);
        if (limit >= PLAYER_INVENTORY_SIZE) {
            return;
        }

        Slot hoveredLockedSlot = null;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);

        for (Slot slot : this.menu.slots) {
            if (!isLockedPlayerSlot(slot, limit, player)) {
                continue;
            }

            int renderX = this.leftPos + slot.x;
            int renderY = this.topPos + slot.y;
            RenderSystem.enableBlend();
            graphics.fill(renderX, renderY, renderX + SLOT_SIZE, renderY + SLOT_SIZE, 0xA0000000);
            graphics.blit(LOCK_TEXTURE, renderX, renderY, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
            RenderSystem.disableBlend();

            if (isMouseOverSlot(renderX, renderY, mouseX, mouseY)) {
                hoveredLockedSlot = slot;
            }
        }

        graphics.pose().popPose();

        if (hoveredLockedSlot != null) {
            int requiredSlots = hoveredLockedSlot.getContainerSlot() + 1;
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    java.util.List.of(
                            Component.translatable("tooltip.medieval_coins.locked_slot"),
                            Component.translatable("tooltip.medieval_coins.locked_slot_requirement", requiredSlots)
                    ),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY
            );
        }
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void hideMerchantOwnerSlots(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof MerchantCounterOwnerScreen screen && !screen.medievalCoins$showInventorySlots()) {
            ci.cancel();
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void disableHiddenMerchantOwnerSlots(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MerchantCounterOwnerScreen screen && !screen.medievalCoins$showInventorySlots()) {
            cir.setReturnValue(false);
        }
    }

    private boolean isLockedPlayerSlot(Slot slot, int limit, Player player) {
        int index = slot.getContainerSlot();
        return slot.container == player.getInventory()
                && index >= 0
                && index < PLAYER_INVENTORY_SIZE
                && index >= limit;
    }

    private boolean isMouseOverSlot(int slotX, int slotY, double mouseX, double mouseY) {
        return mouseX >= slotX
                && mouseX < slotX + SLOT_SIZE
                && mouseY >= slotY
                && mouseY < slotY + SLOT_SIZE;
    }

    private int getInventoryLimit(Player player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel model = PlayerCache.getPlayer(uuid);
        if (model != null) {
            return Math.max(1, Math.min(PLAYER_INVENTORY_SIZE, model.place));
        }
        return PLAYER_INVENTORY_SIZE;
    }
}
