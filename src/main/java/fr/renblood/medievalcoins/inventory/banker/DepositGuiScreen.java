package fr.renblood.medievalcoins.inventory.banker;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.SubmitDepositMessage;
import fr.renblood.medievalcoins.network.DepositAllCoinsMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public class DepositGuiScreen extends AbstractContainerScreen<DepositGuiMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MedievalCoin.MODID, "textures/gui/deposit_gui.png");
    // Position et espacement repris du menu
    private static final int SLOT_START_X = 51, SLOT_SPACING = 36, SLOT_Y = 72;

    public DepositGuiScreen(DepositGuiMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = 226;
        this.imageHeight = 216;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float pt, int mx, int my) {
        // Dessin du fond
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.enableBlend();
        gg.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        RenderSystem.disableBlend();

        // *** Nouveauté : affichage du stock de chaque pièce au-dessus du slot ***
        // On interroge l'inventaire du joueur côté client pour connaître ses quantités
        var inv = this.minecraft.player.getInventory();
        // on récupère par type : iron, bronze, silver, gold
        ItemStack[] balances = new ItemStack[] {
                new ItemStack(Coins.IRON_COIN.get(),   inv.countItem(Coins.IRON_COIN.get())),
                new ItemStack(Coins.BRONZE_COIN.get(), inv.countItem(Coins.BRONZE_COIN.get())),
                new ItemStack(Coins.SILVER_COIN.get(), inv.countItem(Coins.SILVER_COIN.get())),
                new ItemStack(Coins.GOLD_COIN.get(),   inv.countItem(Coins.GOLD_COIN.get()))
        };

        // Pour chacun des 4 slots, on affiche l'icône + compteur
        for (int i = 0; i < 4; i++) {
            int x = leftPos + SLOT_START_X + i * SLOT_SPACING;
            int y = topPos  + SLOT_Y - 20; // 20px au-dessus du slot
            ItemStack stack = balances[i];
            if (!stack.isEmpty()) {
                // Rend l'item
                gg.renderItem(stack, x, y);
                // Rend l'overlay de nombre
                gg.renderItemDecorations(font, stack, x, y, String.valueOf(stack.getCount()));
            }
        }
    }

    @Override public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg);
        super.render(gg, mx, my, pt);
        renderTooltip(gg, mx, my);
    }

    @Override protected void renderLabels(GuiGraphics gg, int mx, int my) {
        // titre centré
        String s = this.title.getString();
        gg.drawString(font, s,
                (imageWidth - font.width(s)) / 2, 12, 0x404040, false);
    }

    @Override public void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.medieval_coins.banker_gui.deposit_all"),
                        btn -> MedievalCoin.PACKET_HANDLER.sendToServer(new DepositAllCoinsMessage()))
                .bounds(leftPos + 38, topPos + 89, 72, 18)
                .build());
        // Bouton “Valider”
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.medieval_coins.banker_gui.deposit_submit"),
                        btn -> {
                            var inv = menu.getDepositInv();
                            // Vérification client-side pour éviter d'envoyer un paquet inutile
                            boolean empty = true;
                            for(int i=0; i<4; i++) {
                                if(!inv.getItem(i).isEmpty()) {
                                    empty = false;
                                    break;
                                }
                            }
                            
                            if (empty) {
                                // Optionnel : jouer un son d'erreur ou afficher un message
                                return;
                            }

                            MedievalCoin.PACKET_HANDLER.sendToServer(new SubmitDepositMessage(
                                    menu.getPos(),
                                    inv.getItem(0),
                                    inv.getItem(1),
                                    inv.getItem(2),
                                    inv.getItem(3)
                            ));
                        })
                .bounds(leftPos + 116, topPos + 89, 72, 18)
                .build());
    }
}
