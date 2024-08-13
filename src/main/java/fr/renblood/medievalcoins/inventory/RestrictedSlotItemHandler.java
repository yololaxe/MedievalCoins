package fr.renblood.medievalcoins.inventory;

import net.minecraftforge.items.SlotItemHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Set;

public class RestrictedSlotItemHandler extends SlotItemHandler {
    private final Set<Item> allowedItems;

    public RestrictedSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition, Set<Item> allowedItems) {
        super(itemHandler, index, xPosition, yPosition);
        this.allowedItems = allowedItems;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return allowedItems.contains(stack.getItem());
    }
}
