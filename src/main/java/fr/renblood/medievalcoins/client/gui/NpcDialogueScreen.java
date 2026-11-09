package fr.renblood.medievalcoins.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class NpcDialogueScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private final String npcName;
    private final String text;
    private final String texture;

    public NpcDialogueScreen(String npcName, String text, String texture) {
        super(Component.translatable("screen.medieval_coins.dialogue"));
        this.npcName = npcName == null || npcName.isBlank() ? "PNJ" : npcName;
        this.text = text == null ? "" : text;
        this.texture = texture == null ? "" : texture;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);

        int panelWidth = Math.min(PANEL_WIDTH, width - 32);
        int left = (width - panelWidth) / 2;
        int bottom = height - 28;
        int top = Math.max(28, bottom - 126);

        graphics.fill(left, top, left + panelWidth, bottom, 0xEE17130F);
        graphics.fill(left, top, left + panelWidth, top + 2, 0xFFFFC857);
        graphics.fill(left, bottom - 2, left + panelWidth, bottom, 0xFF5B4630);
        graphics.drawString(font, npcName, left + 16, top + 14, 0xFFFFC857, false);

        List<FormattedCharSequence> lines = font.split(Component.literal(text), panelWidth - 32);
        int y = top + 34;
        for (FormattedCharSequence line : lines) {
            if (y > bottom - 28) break;
            graphics.drawString(font, line, left + 16, y, 0xFFEFE2C2, false);
            y += 12;
        }

        graphics.drawString(font, Component.translatable("gui.medieval_coins.dialogue.close"),
                left + 16, bottom - 17, 0xFF9D8F78, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
