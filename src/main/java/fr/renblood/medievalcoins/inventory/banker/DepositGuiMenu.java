package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.inventory.RestrictedSlotContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
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

    @Override public ItemStack quickMoveStack(Player player, int idx) {
        return ItemStack.EMPTY;
    }

    public SimpleContainer getDepositInv() {
        return depositInv;
    }
}
