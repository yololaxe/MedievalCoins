package fr.renblood.medievalcoins.events;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, value = Dist.CLIENT)
public final class PauseMenuEvents {
    private static final String RP_SITE_URL = "https://renblood-website.web.app/";
    private static final ResourceLocation RP_LOGO = new ResourceLocation(MedievalCoin.MODID, "textures/gui/renblood_logo.png");
    private static final int LOGO_SIZE = 48;

    private PauseMenuEvents() {
    }

    @SubscribeEvent
    public static void onPauseMenuInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;

        event.addListener(new WebsiteLogoButton(
                event.getScreen().width - LOGO_SIZE - 12,
                event.getScreen().height - LOGO_SIZE - 12
        ));
    }

    private static final class WebsiteLogoButton extends AbstractButton {
        private WebsiteLogoButton(int x, int y) {
            super(x, y, LOGO_SIZE, LOGO_SIZE, Component.literal("Ouvrir le site du RP"));
        }

        @Override
        public void onPress() {
            Util.getPlatform().openUri(RP_SITE_URL);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(RP_LOGO, getX(), getY(), width, height, 0, 0, 200, 200, 200, 200);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationOutput) {
            defaultButtonNarrationText(narrationOutput);
        }
    }
}