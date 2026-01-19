package fr.renblood.medievalcoins.tree.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Inventaire custom pour stocker 1 seul slot spécial (Fertilizer ou Torch).
 */
public class SpecialSlotInventory extends ItemStackHandler implements INBTSerializable<CompoundTag> {

    public SpecialSlotInventory() {
        super(1); // 1 slot uniquement
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        // Accepte Bone Meal ou Torche
        return stack.getItem() == Items.BONE_MEAL || stack.getItem() == Items.TORCH;
    }

    @Override
    public int getSlotLimit(int slot) {
        // Limite de 16 pour bone meal, 1 pour torche (infinie)
        return 64;
    }

    public ItemStack getSpecialItem() {
        return getStackInSlot(0);
    }

    public void setSpecialItem(ItemStack stack) {
        setStackInSlot(0, stack);
    }

    @Override
    public CompoundTag serializeNBT() {
        return super.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
    }
}
