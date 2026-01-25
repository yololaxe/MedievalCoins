package fr.renblood.medievalcoins.inventory.purse;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.inventory.RestrictedSlotContainer;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.item.Purse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

import static fr.renblood.medievalcoins.init.MedievalCoinsModMenus.PURSE_CONTAINER;

public class PurseContainer extends AbstractContainerMenu {
    private final SimpleContainer container;
    private final ItemStack purseStack; // On garde une référence à la stack pour vérifier sa validité

    public PurseContainer(int id, Inventory playerInventory, ItemStack purseStack) {
        super(PURSE_CONTAINER.get(), id);
        this.purseStack = purseStack;

        // Sécurité : si l'item n'est pas une Purse, on crée un container vide pour éviter le crash
        // et stillValid renverra false pour fermer le GUI
        if (purseStack.getItem() instanceof Purse purseItem) {
            this.container = purseItem.getInventory(purseStack);
        } else {
            this.container = new SimpleContainer(9);
        }

        Set<Item> allowedItems = new HashSet<>();
        allowedItems.add(Coins.GOLD_COIN.get());
        allowedItems.add(Coins.SILVER_COIN.get());
        allowedItems.add(Coins.BRONZE_COIN.get());
        allowedItems.add(Coins.IRON_COIN.get());

        // Slots du Purse en 3x3
        int startX = 62;
        int startY = 17;

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new RestrictedSlotContainer(this.container, col + row * 3, startX + col * 18, startY + row * 18, allowedItems));
            }
        }

        // Slots de l'inventaire du joueur
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Slots de la barre d'action du joueur
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            // On vérifie que l'item est toujours une Purse avant de sauvegarder
            // On utilise la stack passée au constructeur, mais on vérifie si elle est toujours valide
            // Si le joueur a déplacé la bourse, il faut la retrouver ou utiliser la référence si elle est toujours valide
            if (this.purseStack.getItem() instanceof Purse purseItem) {
                purseItem.saveInventory(this.purseStack, this.container);
            }
        }
    }

    public static PurseContainer fromNetwork(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        ItemStack purseStack = buf.readItem();
        return new PurseContainer(windowId, playerInv, purseStack);
    }

    @Override
    public boolean stillValid(Player player) {
        // Vérifie si le joueur tient toujours la bourse (main principale ou secondaire)
        // Ou si la stack passée au constructeur est toujours valide et dans l'inventaire
        // Pour simplifier et éviter les crashs lors du swap de main :
        return !this.purseStack.isEmpty() && this.purseStack.getItem() instanceof Purse;
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
                        stackInSlot.getItem() == Coins.SILVER_COIN.get() || stackInSlot.getItem() == Coins.IRON_COIN.get() ||
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
