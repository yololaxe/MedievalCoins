package fr.renblood.medievalcoins.tree.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Inventaire custom pour stocker 1 seul slot Fertilizer.
 */
public class FertilizerInventory extends ItemStackHandler implements INBTSerializable<CompoundTag> {

    public FertilizerInventory() {
        super(1); // 1 slot uniquement
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        // Seule la bone meal peut être stockée ici
        return stack.getItem() == Items.BONE_MEAL;
    }

    @Override
    public int getSlotLimit(int slot) {
        // Limite de 16 fertilizer max
        return 16;
    }

    public ItemStack getFertilizer() {
        return getStackInSlot(0);
    }

    public void setFertilizer(ItemStack stack) {
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
