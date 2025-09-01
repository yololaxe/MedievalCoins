package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.inventory.RestrictedSlotContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class DepositGuiMenu extends AbstractContainerMenu {
    private final SimpleContainer depositInv = new SimpleContainer(4);
    private final BlockPos pos;

    public BlockPos getPos() {
        return pos;
    }

    public DepositGuiMenu(int id, Inventory playerInv, BlockPos pos, int windowId) {
        super(MedievalCoinsModMenus.DEPOSIT_MENU.get(), id);
        this.pos = pos;

        // 1) Slots de dépôt verrouillés chacun sur un seul item
        int slotStartX = 51, slotSpacing = 36, slotY = 72;
        this.addSlot(new RestrictedSlotContainer(depositInv, 0, slotStartX + 0 * slotSpacing, slotY, Set.of(Coins.IRON_COIN.get())));
        this.addSlot(new RestrictedSlotContainer(depositInv, 1, slotStartX + 1 * slotSpacing, slotY, Set.of(Coins.BRONZE_COIN.get())));
        this.addSlot(new RestrictedSlotContainer(depositInv, 2, slotStartX + 2 * slotSpacing, slotY, Set.of(Coins.SILVER_COIN.get())));
        this.addSlot(new RestrictedSlotContainer(depositInv, 3, slotStartX + 3 * slotSpacing, slotY, Set.of(Coins.GOLD_COIN.get())));

        // --- 2) Inventaire principal (3×9) ---
        int invStartX = 25 + 8;
        int invStartY = 25 + 84;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        invStartX + col * 18,
                        invStartY + row * 18));

        // --- 3) Hotbar (1×9) ---
        int hotbarY = 25 + 142;
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col,
                    invStartX + col * 18,
                    hotbarY));
    }

    public static DepositGuiMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new DepositGuiMenu(id, inv, pos, id);
    }

    @Override public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack movedStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            movedStack = originalStack.copy();

            // Indices des zones dans la liste de slots :
            int DEPOSIT_START = 0, DEPOSIT_END = 4;    // slots 0,1,2,3
            int INV_START     = 4, INV_END     = 4 + 27; // slots 4..30 (3×9)
            int HOTBAR_START  = INV_END, HOTBAR_END = INV_END + 9; // slots 31..39

            // 1) Si on Shift+clic depuis l'inventaire du joueur
            if (index >= INV_START) {
                boolean success = false;

                // Tenter d'abord les slots de dépôt, selon le type de pièce :
                if (originalStack.getItem() == Coins.IRON_COIN.get()) {
                    success = this.moveItemStackTo(originalStack, DEPOSIT_START + 0, DEPOSIT_START + 1, false);
                } else if (originalStack.getItem() == Coins.BRONZE_COIN.get()) {
                    success = this.moveItemStackTo(originalStack, DEPOSIT_START + 1, DEPOSIT_START + 2, false);
                } else if (originalStack.getItem() == Coins.SILVER_COIN.get()) {
                    success = this.moveItemStackTo(originalStack, DEPOSIT_START + 2, DEPOSIT_START + 3, false);
                } else if (originalStack.getItem() == Coins.GOLD_COIN.get()) {
                    success = this.moveItemStackTo(originalStack, DEPOSIT_START + 3, DEPOSIT_START + 4, false);
                }

                // Si ce n'était pas une pièce (ou si le slot de dépôt est plein), on transfère entre main et hotbar
                if (!success) {
                    if (index < HOTBAR_START) {
                        // main → hotbar
                        success = this.moveItemStackTo(originalStack, HOTBAR_START, HOTBAR_END, false);
                    } else {
                        // hotbar → main
                        success = this.moveItemStackTo(originalStack, INV_START, INV_END, false);
                    }
                }

                if (!success) {
                    return ItemStack.EMPTY;
                }

            } else {
                // 2) Si on Shift+clic depuis un slot de dépôt, renvoyer dans l'inventaire du joueur
                if (!this.moveItemStackTo(originalStack, INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            }

            // Mettre à jour le slot source après le transfert
            if (originalStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return movedStack;
    }


    public SimpleContainer getDepositInv() {
        return depositInv;
    }
    @Override
    public void removed(Player player) {
        super.removed(player);

        // calcul du point devant le joueur
        Direction look = player.getDirection(); // orientation nord/sud/est/ouest
        double offsetX = look.getStepX() * 0.5;
        double offsetZ = look.getStepZ() * 0.5;
        double spawnX = player.getX() + offsetX;
        double spawnY = player.getY() + 1.0; // légèrement au-dessus du sol
        double spawnZ = player.getZ() + offsetZ;

        // 1) Pour DepositGuiMenu
        for (int i = 0; i < this.depositInv.getContainerSize(); i++) {
            ItemStack stack = this.depositInv.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    // spawn devant le joueur
                    ItemEntity dropped = new ItemEntity(player.level(), spawnX, spawnY, spawnZ, stack);
                    player.level().addFreshEntity(dropped);
                }
            }
        }
    }


}
