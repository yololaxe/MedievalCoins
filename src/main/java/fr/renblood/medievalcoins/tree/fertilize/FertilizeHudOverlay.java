package fr.renblood.medievalcoins.tree.fertilize;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.capability.FertilizerCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Overlay client : affiche le slot Fertilizer (10ème slot à droite de la hotbar).
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MedievalCoin.MODID)
public class FertilizeHudOverlay {

    // Texture vanilla du slot (le carré de l'inventaire)
    private static final ResourceLocation SLOT_BG =
            new ResourceLocation("medieval_coins", "textures/gui/slot.png");


    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // On s'assure de render après la hotbar pour être au dessus
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        if (!FertilizeCommand.fertilizeActiveHUD) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics gui = event.getGuiGraphics();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // coordonnées : juste à droite de la hotbar
        int x = screenWidth / 2 + 91 + 4;
        int y = screenHeight - 22;

        // Setup render state pour éviter les problèmes de couleur/transparence
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Dessine l’arrière-plan du slot
        // On spécifie la taille de la texture (18x18) pour qu'elle ne soit pas étirée depuis 256x256
        gui.blit(SLOT_BG, x, y, 0, 0, 22, 22, 22, 22);
        
        RenderSystem.disableBlend();

        // Affiche l’item stocké dans la capability Fertilizer
        mc.player.getCapability(FertilizerCapabilityHandler.FERTILIZER_CAP).ifPresent(inv -> {
            ItemStack stack = inv.getStackInSlot(0);
            if (!stack.isEmpty()) {
                gui.renderItem(stack, x + 3, y + 3);
                gui.renderItemDecorations(mc.font, stack, x + 3, y + 3);
            } else if (MedievalCoin.DEBUG_MODE) {
                // Debug visuel si vide (carré rouge temporaire)
                // gui.fill(x + 3, y + 3, x + 19, y + 19, 0x80FF0000);C
            }
        });
    }
}
