package fr.renblood.medievalcoins.market.counter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public abstract class AbstractMerchantCounterMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final MerchantCounterData data;

    protected AbstractMerchantCounterMenu(MenuType<?> type, int id, BlockPos pos, MerchantCounterData data) {
        super(type, id);
        this.pos = pos;
        this.data = data;
    }

    public BlockPos getPos() {
        return pos;
    }

    public MerchantCounterData getData() {
        return data;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
