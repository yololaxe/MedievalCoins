package fr.renblood.medievalcoins.inventory.purse;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;


public class PurseScreen extends AbstractContainerScreen<PurseContainer> {
    private static final ResourceLocation PURSE_GUI = new ResourceLocation("medieval_coins", "textures/gui/purse.png");

    public PurseScreen(PurseContainer screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }


    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, PURSE_GUI);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(PURSE_GUI, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

   // @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player playerEntity) {
        ItemStack purseStack = playerEntity.getItemInHand(InteractionHand.MAIN_HAND);
        return new PurseContainer(id, playerInventory, purseStack);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title.getString(), 8, 6, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle.getString(), 8, this.imageHeight - 94, 4210752, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY); // Affiche les infobulles avec les noms des items
    }

}