package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.BankerGuiRefreshMessage;
import fr.renblood.medievalcoins.network.SubmitWithdrawMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WithdrawGuiScreen extends AbstractContainerScreen<WithdrawGuiMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MedievalCoin.MODID, "textures/gui/withdraw_gui.png");
    private static final int CONFIRMATION_THRESHOLD = 4096;

    private final ItemStack[] defaultStacks = new ItemStack[12];
    private double balance = 0;
    private EditBox customAmount;
    private int customCoinType = 0;

    public WithdrawGuiScreen(WithdrawGuiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 226;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        super.init();
        MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiRefreshMessage());

        for (int i = 0; i < 12; i++) {
            if (defaultStacks[i] == null || defaultStacks[i].isEmpty()) {
                defaultStacks[i] = new ItemStack(coinItem(i / 3), presetAmount(i));
            }
        }

        customAmount = new EditBox(font, leftPos + 50, topPos + 88, 40, 18,
                Component.translatable("gui.medieval_coins.withdraw_gui.amount"));
        customAmount.setFilter(value -> value.isEmpty() || value.matches("\\d{1,2}"));
        customAmount.setValue("1");
        addRenderableWidget(customAmount);

        addRenderableWidget(Button.builder(Component.literal(coinTypeName(customCoinType)), button -> {
                    customCoinType = (customCoinType + 1) % 4;
                    button.setMessage(Component.literal(coinTypeName(customCoinType)));
                })
                .bounds(leftPos + 94, topPos + 88, 58, 18)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.medieval_coins.withdraw_gui.submit"), button -> {
                    int amount = parseCustomAmount();
                    if (amount > 0) requestWithdraw(customCoinType, amount);
                })
                .bounds(leftPos + 156, topPos + 88, 55, 18)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        RenderSystem.disableBlend();

        for (int i = 0; i < 12; i++) {
            int type = i / 3;
            int amount = presetAmount(i);
            ItemStack display = balance < unitCost(type) * amount
                    ? new ItemStack(Items.BARRIER)
                    : defaultStacks[i];
            menu.slots.get(i).set(display);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        String blockedReason = blockedReasonAt(mouseX, mouseY);
        if (blockedReason != null) {
            graphics.renderTooltip(font, Component.literal(blockedReason), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rx = (int) mouseX - leftPos;
        int ry = (int) mouseY - topPos;
        for (int i = 0; i < 12; i++) {
            int sx = 51 + (i / 3) * 36;
            int sy = 23 + (i % 3) * 27;
            if (rx >= sx && rx < sx + 16 && ry >= sy && ry < sy + 16) {
                requestWithdraw(i / 3, presetAmount(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == 256) {
            minecraft.player.closeContainer();
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.medieval_coins.withdraw_gui.balance",
                formatMoney((long) balance)), 12, 8, 0x404040, false);
    }

    public void updateMoney(double newBalance) {
        this.balance = newBalance;
    }

    private void requestWithdraw(int type, int amount) {
        long cost = unitCost(type) * amount;
        if (balance < cost) {
            minecraft.player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_insufficient"));
            return;
        }
        if (!hasInventorySpace(new ItemStack(coinItem(type), amount))) {
            minecraft.player.sendSystemMessage(Component.translatable("chat.medieval_coins.withdraw_no_space"));
            return;
        }

        Runnable send = () -> MedievalCoin.PACKET_HANDLER.sendToServer(new SubmitWithdrawMessage(menu.pos, type, amount));
        if (cost >= CONFIRMATION_THRESHOLD) {
            minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) send.run();
                        minecraft.setScreen(this);
                    },
                    Component.translatable("gui.medieval_coins.withdraw_gui.confirm_title"),
                    Component.translatable("gui.medieval_coins.withdraw_gui.confirm",
                            amount, coinTypeName(type), formatMoney(cost))
            ));
        } else {
            send.run();
        }
    }

    private int parseCustomAmount() {
        try {
            int amount = Integer.parseInt(customAmount.getValue());
            return amount >= 1 && amount <= 64 ? amount : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String blockedReasonAt(int mouseX, int mouseY) {
        int rx = mouseX - leftPos;
        int ry = mouseY - topPos;
        for (int i = 0; i < 12; i++) {
            int sx = 51 + (i / 3) * 36;
            int sy = 23 + (i % 3) * 27;
            if (rx >= sx && rx < sx + 16 && ry >= sy && ry < sy + 16) {
                int type = i / 3;
                int amount = presetAmount(i);
                if (balance < unitCost(type) * amount) {
                    return Component.translatable("gui.medieval_coins.withdraw_gui.blocked_funds").getString();
                }
                if (!hasInventorySpace(new ItemStack(coinItem(type), amount))) {
                    return Component.translatable("gui.medieval_coins.withdraw_gui.blocked_inventory").getString();
                }
            }
        }
        return null;
    }

    private boolean hasInventorySpace(ItemStack stack) {
        int needed = stack.getCount();
        for (ItemStack existing : minecraft.player.getInventory().items) {
            if (existing.isEmpty()) needed -= stack.getMaxStackSize();
            else if (ItemStack.isSameItem(existing, stack)) needed -= existing.getMaxStackSize() - existing.getCount();
            if (needed <= 0) return true;
        }
        return false;
    }

    private int presetAmount(int index) {
        return switch (index % 3) {
            case 1 -> 10;
            case 2 -> 32;
            default -> 1;
        };
    }

    private Item coinItem(int type) {
        return switch (type) {
            case 1 -> Coins.BRONZE_COIN.get();
            case 2 -> Coins.SILVER_COIN.get();
            case 3 -> Coins.GOLD_COIN.get();
            default -> Coins.IRON_COIN.get();
        };
    }

    private long unitCost(int type) {
        return switch (type) {
            case 1 -> 64L;
            case 2 -> 4096L;
            case 3 -> 262144L;
            default -> 1L;
        };
    }

    private String coinTypeName(int type) {
        return switch (type) {
            case 1 -> Component.translatable("gui.medieval_coins.coin.bronze").getString();
            case 2 -> Component.translatable("gui.medieval_coins.coin.silver").getString();
            case 3 -> Component.translatable("gui.medieval_coins.coin.gold").getString();
            default -> Component.translatable("gui.medieval_coins.coin.iron").getString();
        };
    }

    private String formatMoney(long amount) {
        long gold = amount / 262144L;
        long silver = amount % 262144L / 4096L;
        long bronze = amount % 4096L / 64L;
        long iron = amount % 64L;
        return gold + " or, " + silver + " argent, " + bronze + " bronze, " + iron + " fer";
    }
}
