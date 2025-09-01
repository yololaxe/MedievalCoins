package fr.renblood.medievalcoins.tree.fertilize;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.tree.capability.FertilizerCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MedievalCoin.MODID)
public class FertilizeHudOverlay {

    private static final ResourceLocation SLOT_BG =
            new ResourceLocation("minecraft", "textures/gui/container/slot.png");

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!FertilizeCommand.fertilizeActiveHUD) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // à droite de la hotbar vanilla
        int x = screenWidth / 2 + 91 + 4;
        int y = screenHeight - 22;

        // dessiner la case vide
        gui.blit(SLOT_BG, x, y, 0, 0, 16, 16);

        // dessiner l’item fertilizer si présent
        mc.player.getCapability(FertilizerCapabilityHandler.FERTILIZER_CAP).ifPresent(inv -> {
            ItemStack stack = inv.getStackInSlot(0);
            if (!stack.isEmpty()) {
                gui.renderItem(stack, x, y);
                gui.renderItemDecorations(mc.font, stack, x, y);
            }
        });
    }
}
