package fr.renblood.medievalcoins.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class RestrictedSlotContainer extends Slot {
    private final Set<Item> allowedItems;

    public RestrictedSlotContainer(Container container, int index, int xPosition, int yPosition, Set<Item> allowedItems) {
        super(container, index, xPosition, yPosition);
        this.allowedItems = allowedItems;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return allowedItems.contains(stack.getItem());
    }
}
