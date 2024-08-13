package fr.renblood.medievalcoins.inventory.purse;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.inventory.RestrictedSlotContainer;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.item.Purse;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class PurseContainer extends AbstractContainerMenu {
    private final SimpleContainer container;



    public PurseContainer(int id, Inventory playerInventory, ItemStack purseStack) {
        super(MedievalCoin.PURSE_CONTAINER.get(), id);

        Purse purseItem = (Purse) purseStack.getItem();
        this.container = purseItem.getInventory(purseStack); // Utiliser `this.container` pour initialiser correctement la variable membre

        Set<Item> allowedItems = new HashSet<>();
        allowedItems.add(Coins.GOLD_COIN.get());
        allowedItems.add(Coins.SILVER_COIN.get());
        allowedItems.add(Coins.BRONZE_COIN.get());

        // Slots du Purse en 3x3
        int startX = 62; // Position horizontale initiale pour centrer la grille
        int startY = 17; // Position verticale initiale pour centrer la grille

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new RestrictedSlotContainer(this.container, col + row * 3, startX + col * 18, startY + row * 18, allowedItems));
            }
        }

        // Slots de l'inventaire du joueur (inchangé)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Slots de la barre d'action du joueur (inchangé)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack purseStack = player.getMainHandItem(); // ou l'endroit où vous avez stocké le purse
            Purse purseItem = (Purse) purseStack.getItem();
            purseItem.saveInventory(purseStack, this.container);
        }
    }


    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack originalStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            originalStack = stackInSlot.copy();

            // Si l'objet est dans la Purse, le déplacer vers l'inventaire du joueur
            if (index < 9) {
                if (!this.moveItemStackTo(stackInSlot, 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Si l'objet est une pièce et que l'objet est dans l'inventaire du joueur, le déplacer dans la Purse
                if (stackInSlot.getItem() == Coins.GOLD_COIN.get() ||
                        stackInSlot.getItem() == Coins.SILVER_COIN.get() ||
                        stackInSlot.getItem() == Coins.BRONZE_COIN.get()) {

                    if (!this.moveItemStackTo(stackInSlot, 0, 9, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == originalStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(playerIn, stackInSlot);
        }

        return originalStack;
    }


}