package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.client.gui.GuiGraphics;
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
        // taille de votre GUI (ajustez à votre texture)
        this.imageWidth  = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gg.blit(
                TEXTURE,
                this.leftPos, this.topPos,         // où l’afficher
                0, 0,                               // coin de la texture
                this.imageWidth, this.imageHeight, // taille à afficher
                this.imageWidth, this.imageHeight  // résolution de la texture
        );
        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gg);
        super.render(gg, mouseX, mouseY, partialTicks);
        this.renderTooltip(gg, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        // Titre en haut à gauche (facultatif si votre texture l’inclut déjà)
        gg.drawString(
                this.font,
                this.title,    // le Component que vous avez transmis depuis l’ouverture
                8, 6,          // position relative au coin du GUI
                0x404040,      // couleur
                false
        );
    }
}
