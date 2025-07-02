// WithdrawGuiScreen.java
package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.network.SubmitWithdrawMessage;
import fr.renblood.medievalcoins.network.BankerGuiRefreshMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WithdrawGuiScreen extends AbstractContainerScreen<WithdrawGuiMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MedievalCoin.MODID, "textures/gui/withdraw_gui.png");

    // stocke les icônes originales des slots
    private final ItemStack[] defaultStacks = new ItemStack[12];
    // solde côté client, mis à jour par votre handler MoneyUpdateMessage
    private double balance = 0;

    public WithdrawGuiScreen(WithdrawGuiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = 226;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        super.init();
        // demande au serveur le dernier solde
        MedievalCoin.PACKET_HANDLER.sendToServer(new BankerGuiRefreshMessage());

        // conserve les icônes d'origine
        var slots = menu.get();
        for (int i = 0; i < 12; i++) {
            defaultStacks[i] = slots.get(i).getItem().copy();
        }
    }

    @Override
    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        // fond
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gg.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight,imageWidth, imageHeight);
        RenderSystem.disableBlend();

        // met à jour chaque slot : barrière si pas assez, sinon icône d'origine
        var slots = menu.get();
        for (int i = 0; i < 12; i++) {
            int type   = i / 3;
            int amount = switch (i % 3) {
                case 1 -> 10;
                case 2 -> 32;
                default -> 1;
            };
            int cost = switch (type) {
                case 0 -> amount;
                case 1 -> amount * 64;
                case 2 -> amount * 64 * 64;
                case 3 -> amount * 64 * 64 * 64;
                default -> Integer.MAX_VALUE;
            };
            ItemStack toDisplay = balance < cost
                    ? new ItemStack(Items.BARRIER)
                    : defaultStacks[i];
            slots.get(i).set(toDisplay);
        }
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg);
        super.render(gg, mx, my, pt);
        renderTooltip(gg, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int rx = (int)mx - leftPos;
        int ry = (int)my - topPos;

        for (int i = 0; i < 12; i++) {
            int sx = 51 + (i/3)*36;
            int sy = 23 + (i%3)*27;
            if (rx >= sx && rx < sx + 16 && ry >= sy && ry < sy + 16) {
                int type   = i / 3;
                int amount = switch (i % 3) {
                    case 1 -> 10;
                    case 2 -> 32;
                    default -> 1;
                };
                int cost = switch (type) {
                    case 0 -> amount;
                    case 1 -> amount * 64;
                    case 2 -> amount * 64 * 64;
                    case 3 -> amount * 64 * 64 * 64;
                    default -> Integer.MAX_VALUE;
                };
                if (balance < cost) {
                    minecraft.player.sendSystemMessage(
                            Component.translatable("chat.medieval_coins.withdraw_insufficient")
                    );
                    this.init();
                    return true;
                }
                // solde suffisant
                BlockPos pos = menu.pos;
                MedievalCoin.PACKET_HANDLER.sendToServer(
                        new SubmitWithdrawMessage(pos, type, amount)
                );
                minecraft.player.closeContainer();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
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
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        // aucun label supplémentaire
    }

    /** Appelé par votre handler client MoneyUpdateMessage pour mettre à jour le solde */
    public void updateMoney(double newBalance) {
        this.balance = newBalance;
    }
}
