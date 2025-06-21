package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.BankerGuiButtonMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class BankerGuiScreen extends AbstractContainerScreen<BankerGuiMenu> {
	private final static HashMap<String, Object> guistate = BankerGuiMenu.guistate;
	private final Level world;
	private final int x, y, z;
	private final Player entity;
	Button button_change_money;
	Button button_withdraw_money;
	Button button_deposit_money;

	public BankerGuiScreen(BankerGuiMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 249;
		this.imageHeight = 166;
	}

	private static final ResourceLocation texture = new ResourceLocation("medieval_coins:textures/gui/banker_gui.png");

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		guiGraphics.blit(texture, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
		RenderSystem.disableBlend();
	}

	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (key == 256) {
			this.minecraft.player.closeContainer();
			return true;
		}
		return super.keyPressed(key, b, c);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		String money =  "10";
		guiGraphics.drawString(this.font, money , 49, 35, -16777216, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.medieval_coins.banker_gui.label_12_bronze_1208"), 147, 32, -16777216, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.medieval_coins.banker_gui.label_empty"), 149, 49, -16777216, false);
	}

	@Override
	public void init() {
		super.init();
		button_change_money = Button.builder(Component.translatable("gui.medieval_coins.banker_gui.button_change_money"), e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiButtonMessage(0, x, y, z));
				BankerGuiButtonMessage.handleButtonAction(entity, 0, x, y, z);
			}
		}).bounds(this.leftPos + 15, this.topPos + 77, 87, 20).build();
		guistate.put("button:button_change_money", button_change_money);
		this.addRenderableWidget(button_change_money);
		button_withdraw_money = Button.builder(Component.translatable("gui.medieval_coins.banker_gui.button_withdraw_money"), e -> {
		}).bounds(this.leftPos + 10, this.topPos + 125, 98, 20).build();
		guistate.put("button:button_withdraw_money", button_withdraw_money);
		this.addRenderableWidget(button_withdraw_money);
		button_deposit_money = Button.builder(Component.translatable("gui.medieval_coins.banker_gui.button_deposit_money"), e -> {
		}).bounds(this.leftPos + 13, this.topPos + 102, 93, 20).build();
		guistate.put("button:button_deposit_money", button_deposit_money);
		this.addRenderableWidget(button_deposit_money);
	}
}
