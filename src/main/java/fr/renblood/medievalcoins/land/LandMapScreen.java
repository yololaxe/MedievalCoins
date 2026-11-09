package fr.renblood.medievalcoins.land;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LandMapScreen extends AbstractContainerScreen<LandMapMenu> {
    private static final int MAP_X = 8;
    private static final int MAP_Y = 18;
    private static final int SLOT_SIZE = 18;

    public LandMapScreen(LandMapMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 42 + menu.getRows() * SLOT_SIZE;
        this.inventoryLabelY = this.imageHeight + 1000;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF171A1F);
        guiGraphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, 0xFF4A5568);
        guiGraphics.fill(x + 4, y + 14, x + 172, y + imageHeight - 10, 0xFF0F1216);
        guiGraphics.fill(x + 6, y + 16, x + 170, y + imageHeight - 12, 0xFF20242B);

        int gridWidth = menu.getColumns() * SLOT_SIZE;
        int gridHeight = menu.getRows() * SLOT_SIZE;
        int gridX = x + MAP_X;
        int gridY = y + MAP_Y;
        guiGraphics.fill(gridX - 1, gridY - 1, gridX + gridWidth + 1, gridY + gridHeight + 1, 0xFF2D3748);

        for (int col = 0; col <= menu.getColumns(); col++) {
            int lineX = gridX + col * SLOT_SIZE;
            guiGraphics.fill(lineX, gridY, lineX + 1, gridY + gridHeight, 0x553A4656);
        }
        for (int row = 0; row <= menu.getRows(); row++) {
            int lineY = gridY + row * SLOT_SIZE;
            guiGraphics.fill(gridX, lineY, gridX + gridWidth, lineY + 1, 0x553A4656);
        }

        int centerX = gridX + (menu.getColumns() / 2) * SLOT_SIZE;
        int centerY = gridY + (menu.getRows() / 2) * SLOT_SIZE;
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + SLOT_SIZE + 1, centerY + SLOT_SIZE + 1, 0xAAE0B94F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0xF2E6C9, false);
        guiGraphics.drawString(this.font, "N", 84, 6, 0xFF9CA3AF, false);
        guiGraphics.drawString(this.font, "S", 84, this.imageHeight - 8, 0xFF9CA3AF, false);
        guiGraphics.drawString(this.font, "W", 2, 6, 0xFF9CA3AF, false);
        guiGraphics.drawString(this.font, "E", 164, 6, 0xFF9CA3AF, false);
        guiGraphics.drawString(this.font, "Hover for owner details", 8, this.imageHeight - 8,
            0xFF9CA3AF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
