package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Supplier;

public class CtoBProcedure {
	public static void execute(Entity entity) {
		if (entity == null) return;

		if (entity instanceof Player player && player.containerMenu instanceof Supplier supplier && supplier.get() instanceof Map slots) {
			Slot inputSlot = (Slot) slots.get(0);
			Slot outputSlot = (Slot) slots.get(3);

			if (inputSlot != null && outputSlot != null) {
				ItemStack inputStack = inputSlot.getItem();
				
				// Vérifie si on a au moins 64 items dans le slot d'entrée
				if (inputStack.getCount() >= 64) {
					// Retire 64 items du slot d'entrée
					inputStack.shrink(64);
					inputSlot.set(inputStack); // Met à jour le slot (si vide, shrink le gère)

					// Ajoute 1 item au slot de sortie
					ItemStack outputStack = outputSlot.getItem();
					if (outputStack.isEmpty()) {
						outputSlot.set(new ItemStack(Coins.BRONZE_COIN.get(), 1));
					} else if (outputStack.getItem() == Coins.BRONZE_COIN.get()) {
						// Si le slot contient déjà du bronze, on incrémente
						outputStack.grow(1);
						outputSlot.set(outputStack); // Met à jour le slot
					}
					
					player.containerMenu.broadcastChanges();
				}
			}
		}
	}
}
