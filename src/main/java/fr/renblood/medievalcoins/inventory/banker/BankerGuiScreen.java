// src/main/java/fr/renblood/medievalcoins/inventory/banker/BankerGuiScreen.java
package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.BankerGuiRefreshMessage;
import fr.renblood.medievalcoins.network.BankerGuiButtonMessage;
import fr.renblood.medievalcoins.procedures.OpenDepositGuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;

public class BankerGuiScreen extends AbstractContainerScreen<BankerGuiMenu> {
	private static final ResourceLocation TEXTURE =
			new ResourceLocation("medieval_coins:textures/gui/banker_gui.png");

	private final Level world;
	private final int x, y, z;
	private final Player entity;

	private Button btnChange, btnDeposit, btnWithdraw;
	private String moneyDisplay = "0F";
	private String moneyTooltip = "0 pièce de fer";

	private static final HashMap<String, Object> guistate = (HashMap<String, Object>) BankerGuiMenu.guistate;

	public BankerGuiScreen(BankerGuiMenu container, Inventory inv, Component title) {
		super(container, inv, title);
		this.world  = container.world;
		this.x      = container.x;
		this.y      = container.y;
		this.z      = container.z;
		this.entity = container.entity;
		this.imageWidth  = 249;
		this.imageHeight = 166;
	}

	@Override
	public void init() {
		super.init();

		// 1) on demande au serveur de rafraîchir
		MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiRefreshMessage());

		// 2) boutons
		btnChange = Button.builder(
						Component.translatable("gui.medieval_coins.banker_gui.button_change_money"),
						b -> MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiButtonMessage(0, x, y, z))
				)
				.bounds(this.leftPos + 15, this.topPos + 77, 87, 20)
				.build();
		guistate.put("button:change", btnChange);
		addRenderableWidget(btnChange);

		btnDeposit = Button.builder(
						Component.translatable("gui.medieval_coins.banker_gui.button_deposit_money"),
						b -> MedievalCoin.PACKET_HANDLER.sendToServer(new OpenDepositGuiMessage(x, y, z))
				)
				.bounds(this.leftPos + 13, this.topPos + 102, 93, 20)
				.build();
		guistate.put("button:deposit", btnDeposit);
		addRenderableWidget(btnDeposit);

		btnWithdraw = Button.builder(
						Component.translatable("gui.medieval_coins.banker_gui.button_withdraw_money"),
						b -> { /* TODO */ }
				)
				.bounds(this.leftPos + 10, this.topPos + 125, 98, 20)
				.build();
		guistate.put("button:withdraw", btnWithdraw);
		addRenderableWidget(btnWithdraw);
	}

	@Override
	protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
		RenderSystem.setShaderColor(1,1,1,1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		gg.blit(TEXTURE,
				this.leftPos, this.topPos,
				0, 0,
				this.imageWidth, this.imageHeight,
				this.imageWidth, this.imageHeight);
		RenderSystem.disableBlend();
	}

	@Override
	public void render(GuiGraphics gg, int mx, int my, float pt) {
		renderBackground(gg);
		super.render(gg, mx, my, pt);
		if (isHoveringMoney(mx, my)) {
			gg.renderComponentTooltip(
					this.font,
					List.of(Component.literal(moneyTooltip)),
					mx, my
			);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics gg, int mx, int my) {
		gg.drawString(this.font, moneyDisplay, 49, 35, 0xFF000000, false);
		gg.drawString(this.font,
				Component.translatable("gui.medieval_coins.banker_gui.label_12_bronze_1208"),
				147, 32, 0xFF000000, false);
		gg.drawString(this.font,
				Component.translatable("gui.medieval_coins.banker_gui.label_empty"),
				149, 49, 0xFF000000, false);
	}

	// appelé côté client par MoneyUpdateMessage.handle(...)
	public void updateMoney(double newMoney) {
		int amount = (int)newMoney;
		var parts = convertMoney(amount);
		this.moneyDisplay = parts.display;
		this.moneyTooltip = parts.tooltip;
	}

	private boolean isHoveringMoney(int mx, int my) {
		int tx = this.leftPos + 49;
		int ty = this.topPos + 35;
		int w  = this.font.width(moneyDisplay);
		int h  = this.font.lineHeight;
		return mx>=tx && mx<tx+w && my>=ty && my<ty+h;
	}

	private record Parts(int gold, int silver, int bronze, int iron, String display, String tooltip) {}

	private Parts convertMoney(int amount) {
		final int PER_IRON   = 1;
		final int PER_BRONZE = 64 * PER_IRON;
		final int PER_SILVER = 64 * PER_BRONZE;
		final int PER_GOLD   = 64 * PER_SILVER;

		int gold   = amount / PER_GOLD;
		int remG   = amount % PER_GOLD;
		int silver = remG / PER_SILVER;
		int remS   = remG % PER_SILVER;
		int bronze = remS / PER_BRONZE;
		int iron   = remS % PER_BRONZE;

		var disp = new StringBuilder();
		if (gold>0)   disp.append(gold).append("O ");
		if (silver>0) disp.append(silver).append("A ");
		if (bronze>0) disp.append(bronze).append("B ");
		if (iron>0)   disp.append(iron).append("F");
		String display = disp.toString().trim();
		if (display.isEmpty()) display = "0F";

		var tip = new StringBuilder();
		if (gold>0)   tip.append(gold).append(" pièce").append(gold>1?"s":"").append(" d'or");
		if (silver>0) tip.append(tip.length()>0?", ":"").append(silver).append(" pièce").append(silver>1?"s":"").append(" d'argent");
		if (bronze>0) tip.append(tip.length()>0?", ":"").append(bronze).append(" pièce").append(bronze>1?"s":"").append(" de bronze");
		if (iron>0)   tip.append(tip.length()>0?", ":"").append(iron).append(" pièce").append(iron>1?"s":"").append(" de fer");
		String tooltip = tip.toString();
		if (tooltip.isEmpty()) tooltip = "0 pièce de fer";

		return new Parts(gold, silver, bronze, iron, display, tooltip);
	}
}
