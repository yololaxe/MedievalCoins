package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Supplier;

public class GtoSProcedure {
	public static void execute(Entity entity) {
		if (entity == null) return;

		if (entity instanceof Player player && player.containerMenu instanceof Supplier supplier && supplier.get() instanceof Map slots) {
			Slot inputSlot = (Slot) slots.get(8);
			Slot outputSlot = (Slot) slots.get(11);

			if (inputSlot != null && outputSlot != null) {
				ItemStack inputStack = inputSlot.getItem();
				
				if (inputStack.getCount() >= 1) {
					// Vérification place sortie avant de consommer
					ItemStack outputStack = outputSlot.getItem();
					if (!outputStack.isEmpty() && outputStack.getCount() + 64 > outputStack.getMaxStackSize()) {
						return; // Pas de place
					}

					inputStack.shrink(1);
					inputSlot.set(inputStack);

					if (outputStack.isEmpty()) {
						outputSlot.set(new ItemStack(Coins.SILVER_COIN.get(), 64));
					} else if (outputStack.getItem() == Coins.SILVER_COIN.get()) {
						outputStack.grow(64);
						outputSlot.set(outputStack);
					}
					
					player.containerMenu.broadcastChanges();
				}
			}
		}
	}
}
