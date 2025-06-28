// src/main/java/fr/renblood/medievalcoins/inventory/banker/BankerGuiScreen.java
package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.BankerGuiRefreshMessage;
import fr.renblood.medievalcoins.network.BankerGuiButtonMessage;
import fr.renblood.medievalcoins.procedures.OpenDepositGuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
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
	private String moneyDisplay = "0F";
	private String moneyTooltip = "0 pièce de fer";
	private String goldValue = "0";
	private String silverValue = "0";
	private String bronzeValue = "0";
	private String ironValue = "0";

	private static final HashMap<String, Object> guistate = (HashMap<String, Object>) BankerGuiMenu.guistate;

	ImageButton btnDeposit, btnChange, btnWithdraw;

	public BankerGuiScreen(BankerGuiMenu container, Inventory inv, Component title) {
		super(container, inv, title);
		this.world  = container.world;
		this.x      = container.x;
		this.y      = container.y;
		this.z      = container.z;
		this.entity = container.entity;
		this.imageWidth  = 200;
		this.imageHeight = 180;
	}

	@Override
	public void init() {
		super.init();

		MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiRefreshMessage());

		btnDeposit = new ImageButton(this.leftPos + 5, this.topPos + 8, 32, 32, 0, 0, 32,
				new ResourceLocation("medieval_coins:textures/screens/atlas/imagebutton_deposit.png"),
				32, 64, e -> MedievalCoin.PACKET_HANDLER.sendToServer(new OpenDepositGuiMessage(x, y, z)));
		guistate.put("button:imagebutton_deposit", btnDeposit);
		addRenderableWidget(btnDeposit);

		btnChange = new ImageButton(this.leftPos + 52, this.topPos + 27, 32, 32, 0, 0, 32,
				new ResourceLocation("medieval_coins:textures/screens/atlas/imagebutton_wholepurse.png"),
				32, 64, e -> MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiButtonMessage(0, x, y, z)));
		guistate.put("button:imagebutton_wholepurse", btnChange);
		addRenderableWidget(btnChange);

		btnWithdraw = new ImageButton(this.leftPos + 5, this.topPos + 47, 32, 32, 0, 0, 32,
				new ResourceLocation("medieval_coins:textures/screens/atlas/imagebutton_withdraw.png"),
				32, 64, e -> {});
		guistate.put("button:imagebutton_withdraw", btnWithdraw);
		addRenderableWidget(btnWithdraw);
	}

	@Override
	protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		gg.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);

		gg.blit(new ResourceLocation("medieval_coins:textures/item/gold_coin.png"),   this.leftPos + 119,  this.topPos + 20, 0, 0, 16, 16, 16, 16);
		gg.blit(new ResourceLocation("medieval_coins:textures/item/silver_coin.png"),  this.leftPos + 158,  this.topPos + 20, 0, 0, 16, 16, 16, 16);
		gg.blit(new ResourceLocation("medieval_coins:textures/item/bronze_coin.png"),  this.leftPos + 119, this.topPos + 49, 0, 0, 16, 16, 16, 16);
		gg.blit(new ResourceLocation("medieval_coins:textures/item/iron_coin.png"),  this.leftPos + 158,  this.topPos + 49, 0, 0, 16, 16, 16, 16);
		RenderSystem.disableBlend();
	}

	@Override
	public void render(GuiGraphics gg, int mx, int my, float pt) {
		this.renderBackground(gg);
		super.render(gg, mx, my, pt);
		this.renderTooltip(gg, mx, my);
//		if (isHoveringMoney(mx, my)) {
//			gg.renderComponentTooltip(this.font, List.of(Component.literal(moneyTooltip)), mx, my);
//		}
	}

	@Override
	protected void renderLabels(GuiGraphics gg, int mx, int my) {
		// Affiche chaque valeur à côté de son icône
		gg.drawString(this.font, goldValue,   104, 23, 0xFF000000, false); // 🟡
		gg.drawString(this.font, silverValue, 145, 23, 0xFF000000, false); // ⚪
		gg.drawString(this.font, bronzeValue, 104, 52, 0xFF000000, false); // 🟠
		gg.drawString(this.font, ironValue,   145, 52, 0xFF000000, false); // ⚫

		// Labels supplémentaires (si utiles)
		// gg.drawString(this.font, Component.translatable("..."), x, y, color, false);
	}


	public void updateMoney(double newMoney) {
		int amount = (int)newMoney;
		var parts = convertMoney(amount);
		this.moneyDisplay  = parts.display;
		this.moneyTooltip  = parts.tooltip;
		this.goldValue     = String.valueOf(parts.gold());
		this.silverValue   = String.valueOf(parts.silver());
		this.bronzeValue   = String.valueOf(parts.bronze());
		this.ironValue     = String.valueOf(parts.iron());
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
		if (gold > 0)   disp.append(gold).append("O ");
		if (silver > 0) disp.append(silver).append("A ");
		if (bronze > 0) disp.append(bronze).append("B ");
		if (iron > 0)   disp.append(iron).append("F");
		String display = disp.toString().trim();
		if (display.isEmpty()) display = "0F";

		var tip = new StringBuilder();
		if (gold > 0)   tip.append(gold).append(" pièce").append(gold > 1 ? "s" : "").append(" d'or");
		if (silver > 0) tip.append(tip.length() > 0 ? ", " : "").append(silver).append(" pièce").append(silver > 1 ? "s" : "").append(" d'argent");
		if (bronze > 0) tip.append(tip.length() > 0 ? ", " : "").append(bronze).append(" pièce").append(bronze > 1 ? "s" : "").append(" de bronze");
		if (iron > 0)   tip.append(tip.length() > 0 ? ", " : "").append(iron).append(" pièce").append(iron > 1 ? "s" : "").append(" de fer");
		String tooltip = tip.toString();
		if (tooltip.isEmpty()) tooltip = "0 pièce de fer";

		return new Parts(gold, silver, bronze, iron, display, tooltip);
	}
}
