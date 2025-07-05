// src/main/java/fr/renblood/medievalcoins/inventory/banker/ChangeGUIScreen.java
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
	private static final ResourceLocation TEXTURE =
			new ResourceLocation("medieval_coins:textures/gui/change_gui.png");
	private static final HashMap<String, Object> guistate = ChangeGUIMenu.guistate;

	private final Level world;
	private final int x, y, z;
	private final Player entity;

	private ImageButton btnCtoB, btnBtoS, btnStoG, btnBtoC, btnStoB, btnGtoS;

	public ChangeGUIScreen(ChangeGUIMenu container, Inventory inv, Component title) {
		super(container, inv, title);
		this.world  = container.world;
		this.x      = container.pos.getX();
		this.y      = container.pos.getY();
		this.z      = container.pos.getZ();
		this.entity = container.entity;
		this.imageWidth  = 226;
		this.imageHeight = 216;
	}

	@Override
	public void init() {
		super.init();

		// Chacun des boutons n'envoie plus qu'un packet au serveur :
		btnCtoB = new ImageButton(
				leftPos + 44, topPos +  4, 32, 32,
				0, 0, 32,
				new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"),
				32, 64,
				e -> MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(0, x, y, z))
		);
		guistate.put("button:ctoB", btnCtoB);
		addRenderableWidget(btnCtoB);

		btnBtoS = new ImageButton(
				leftPos + 44, topPos + 37, 32, 32,
				0, 0, 32,
				new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"),
				32, 64,
				e -> MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(1, x, y, z))
		);
		guistate.put("button:btoS", btnBtoS);
		addRenderableWidget(btnBtoS);

		btnStoG = new ImageButton(
				leftPos + 44, topPos + 70, 32, 32,
				0, 0, 32,
				new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"),
				32, 64,
				e -> MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(2, x, y, z))
		);
		guistate.put("button:s to G", btnStoG);
		addRenderableWidget(btnStoG);

		btnBtoC = new ImageButton(
				leftPos +151, topPos +  4, 32, 32,
				0, 0, 32,
				new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"),
				32, 64,
				e -> MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(3, x, y, z))
		);
		guistate.put("button:btoC", btnBtoC);
		addRenderableWidget(btnBtoC);

		btnStoB = new ImageButton(
				leftPos +151, topPos + 37, 32, 32,
				0, 0, 32,
				new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"),
				32, 64,
				e -> MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(4, x, y, z))
		);
		guistate.put("button:s to B", btnStoB);
		addRenderableWidget(btnStoB);

		btnGtoS = new ImageButton(
				leftPos +151, topPos + 70, 32, 32,
				0, 0, 32,
				new ResourceLocation("medieval_coins:textures/gui/elements/transform.png"),
				32, 64,
				e -> MedievalCoin.PACKET_HANDLER.sendToServer(new ChangeGUIButtonMessage(5, x, y, z))
		);
		guistate.put("button:g to S", btnGtoS);
		addRenderableWidget(btnGtoS);
	}

	@Override
	protected void renderBg(GuiGraphics gg, float partialTicks, int mouseX, int mouseY) {
		RenderSystem.setShaderColor(1f,1f,1f,1f);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		gg.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
		RenderSystem.disableBlend();

		// (Vos icônes de pièces déjà dans cette méthode…)
	}

	@Override
	public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(gg);
		super.render(gg, mouseX, mouseY, partialTicks);
		this.renderTooltip(gg, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) { }

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) { // Échap
			this.minecraft.player.closeContainer();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		super.onClose();
	}
}
