package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class DepositGuiMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final int windowId;

    public DepositGuiMenu(int id, Inventory playerInv, BlockPos pos, int windowId) {
        super(MedievalCoinsModMenus.DEPOSIT_MENU.get(), id);
        this.pos = pos;
        this.windowId = windowId;

        // TODO : créer et ajouter vos slots de dépôt (fer, bronze, argent, or)
        // ex : this.addSlot(new Slot(purseInv, index, x, y));
    }

    public static DepositGuiMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int windowId = buf.readVarInt();
        return new DepositGuiMenu(id, inv, pos, windowId);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
