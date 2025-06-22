package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.SubmitDepositMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

// L’écran client pour votre menu de dépôt
public class DepositGuiScreen extends AbstractContainerScreen<DepositGuiMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MedievalCoin.MODID, "textures/gui/deposit_gui.png");

    public DepositGuiScreen(DepositGuiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // calcul standard : 166px pour les slots dépôt + 82px pour l’inventaire joueur
        this.imageWidth  = 176;
        this.imageHeight = 166 + 82;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.enableBlend();
        gg.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, imageWidth, imageHeight);
        RenderSystem.disableBlend();
    }

    @Override public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg);
        super.render(gg, mx, my, pt);
        renderTooltip(gg, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        // Titre centré
        String s = this.title.getString();
        gg.drawString(font, s, (imageWidth - font.width(s)) / 2, 6, 0x404040, false);
        // label inventaire
        gg.drawString(font,
                Component.translatable("container.inventory").getString(),
                8, imageHeight - 96 + 2, 0x404040, false);
    }
    @Override
    public void init() {
        super.init();

        // bouton “Submit”
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.medieval_coins.banker_gui.deposit_submit"),
                        btn -> {
                            var inv = menu.getDepositInv();
                            // envoi du paquet au serveur
                            MedievalCoin.PACKET_HANDLER.sendToServer(new SubmitDepositMessage(

                                    menu.getPos(),
                                    inv.getItem(0),  // slot fer
                                    inv.getItem(1),  // slot bronze
                                    inv.getItem(2),  // slot argent
                                    inv.getItem(3)   // slot or
                            ));
                        })
                .bounds(leftPos + imageWidth - 70, topPos + imageHeight - 20, 60, 20)
                .build()
        );
    }
}