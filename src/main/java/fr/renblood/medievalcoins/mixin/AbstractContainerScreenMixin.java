package fr.renblood.medievalcoins.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected T menu;

    private static final ResourceLocation LOCK_TEXTURE = new ResourceLocation(MedievalCoin.MODID, "textures/gui/lock.png");

    // On injecte à la fin de render pour dessiner par-dessus tout
    // render(GuiGraphics, int, int, float)V
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void onRender(GuiGraphics gg, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // On vérifie si le menu contient l'inventaire du joueur
        // C'est le cas pour presque tous les menus, mais on veut cibler les slots du joueur
        
        int limit = getInventoryLimit(player);
        if (limit >= 36) return; // Tout ouvert

        gg.pose().pushPose();
        gg.pose().translate(0, 0, 200); // Z-index élevé

        for (Slot slot : this.menu.slots) {
            if (slot.container == player.getInventory()) {
                int index = slot.getContainerSlot();
                // Slots principaux (0-35)
                if (index >= 0 && index < 36 && index >= limit) {
                    int x = this.leftPos + slot.x; // Attention: slot.x est relatif au gui, mais render dessine en absolu parfois ?
                    // Non, slot.x est relatif au coin haut-gauche du GUI.
                    // Mais AbstractContainerScreen dessine souvent avec une translation.
                    // Dans render(), on est en coordonnées écran.
                    // Donc il faut ajouter leftPos et topPos.
                    // MAIS ATTENTION : slot.x/y sont mis à jour par le container.
                    // Vérifions : dans renderSlot, on utilise slot.x et slot.y directement car le PoseStack est déjà translaté ?
                    // Non, renderSlot utilise "i + slot.x" où i est leftPos.
                    
                    int renderX = this.leftPos + slot.x;
                    int renderY = this.topPos + slot.y;

                    RenderSystem.enableBlend();
                    // Fond gris
                    gg.fill(renderX, renderY, renderX + 16, renderY + 16, 0xA0000000);
                    // Cadenas
                    gg.blit(LOCK_TEXTURE, renderX, renderY, 0, 0, 16, 16, 16, 16);
                    RenderSystem.disableBlend();
                }
            }
        }
        gg.pose().popPose();
    }

    // isHovering(Slot, double, double)Z
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void onIsHovering(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (isLocked(slot)) {
            cir.setReturnValue(false);
        }
    }

    private boolean isLocked(Slot slot) {
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory inv) {
            Player player = inv.player;
            if (player == Minecraft.getInstance().player) {
                int index = slot.getContainerSlot();
                if (index >= 0 && index < 36) {
                    int limit = getInventoryLimit(player);
                    return index >= limit;
                }
            }
        }
        return false;
    }

    private int getInventoryLimit(Player player) {
        String uuid = player.getGameProfile().getId().toString();
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null) {
            return Math.max(1, Math.min(36, pm.place));
        }
        return 36;
    }
}
