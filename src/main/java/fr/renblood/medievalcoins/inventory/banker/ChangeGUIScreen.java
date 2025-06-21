package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.ChangeGUIButtonMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class ChangeGUIScreen extends AbstractContainerScreen<ChangeGUIMenu> {
	private final static HashMap<String, Object> guistate = ChangeGUIMenu.guistate;
	private final Level world;
	private final int x, y, z;
	private final Player entity;
	ImageButton imagebutton_next;
	ImageButton imagebutton_btos;
	ImageButton imagebutton_btos1;
	ImageButton imagebutton_btos2;
	ImageButton imagebutton_btos3;
	ImageButton imagebutton_btos4;

	public ChangeGUIScreen(ChangeGUIMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 226;
		this.imageHeight = 216;
	}

	private static final ResourceLocation texture = new ResourceLocation("medieval_coins:textures/gui/change_gui.png");

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

		guiGraphics.blit(new ResourceLocation("medieval_coins:textures/item/bronze_coin.png"), this.leftPos + 106, this.topPos + 12, 0, 0, 16, 16, 16, 16);

		guiGraphics.blit(new ResourceLocation("medieval_coins:textures/item/silver_coin.png"), this.leftPos + 106, this.topPos + 45, 0, 0, 16, 16, 16, 16);

		guiGraphics.blit(new ResourceLocation("medieval_coins:textures/item/gold_coin.png"), this.leftPos + 106, this.topPos + 78, 0, 0, 16, 16, 16, 16);

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
	}

	@Override
	public void init() {
		super.init();
		imagebutton_next = new ImageButton(this.leftPos + 44, this.topPos + 4, 32, 32, 0, 0, 32, new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"), 32, 64, e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(0, x, y, z));
				ChangeGUIButtonMessage.handleButtonAction(entity, 0, x, y, z);
			}
		});
		guistate.put("button:imagebutton_next", imagebutton_next);
		this.addRenderableWidget(imagebutton_next);
		imagebutton_btos = new ImageButton(this.leftPos + 44, this.topPos + 37, 32, 32, 0, 0, 32, new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"), 32, 64, e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(1, x, y, z));
				ChangeGUIButtonMessage.handleButtonAction(entity, 1, x, y, z);
			}
		});
		guistate.put("button:imagebutton_btos", imagebutton_btos);
		this.addRenderableWidget(imagebutton_btos);
		imagebutton_btos1 = new ImageButton(this.leftPos + 44, this.topPos + 70, 32, 32, 0, 0, 32, new	 ResourceLocation("medieval_coins:textures/gui/elements/transform.png"), 32, 64, e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(2, x, y, z));
				ChangeGUIButtonMessage.handleButtonAction(entity, 2, x, y, z);
			}
		});
		guistate.put("button:imagebutton_btos1", imagebutton_btos1);
		this.addRenderableWidget(imagebutton_btos1);
		imagebutton_btos2 = new ImageButton(this.leftPos + 151, this.topPos + 4, 32, 32, 0, 0, 32, new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"), 32, 64, e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(3, x, y, z));
				ChangeGUIButtonMessage.handleButtonAction(entity, 3, x, y, z);
			}
		});
		guistate.put("button:imagebutton_btos2", imagebutton_btos2);
		this.addRenderableWidget(imagebutton_btos2);
		imagebutton_btos3 = new ImageButton(this.leftPos + 151, this.topPos + 37, 32, 32, 0, 0, 32, new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"), 32, 64, e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(4, x, y, z));
				ChangeGUIButtonMessage.handleButtonAction(entity, 4, x, y, z);
			}
		});
		guistate.put("button:imagebutton_btos3", imagebutton_btos3);
		this.addRenderableWidget(imagebutton_btos3);
		imagebutton_btos4 = new ImageButton(this.leftPos + 151, this.topPos + 70, 32, 32, 0, 0, 32, new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"), 32, 64, e -> {
			if (true) {
				MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(5, x, y, z));
				ChangeGUIButtonMessage.handleButtonAction(entity, 5, x, y, z);
			}
		});
		guistate.put("button:imagebutton_btos4", imagebutton_btos4);
		this.addRenderableWidget(imagebutton_btos4);
	}


	@Override
	public void onClose() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.closeContainer(); // Close the container associated with the screen
		}
		super.onClose(); // Close the screen
	}

}
