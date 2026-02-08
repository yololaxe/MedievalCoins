package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Supplier;

public class BtoCProcedure {
	public static void execute(Entity entity) {
		if (entity == null) return;

		if (entity instanceof Player player && player.containerMenu instanceof Supplier supplier && supplier.get() instanceof Map slots) {
			Slot inputSlot = (Slot) slots.get(6);
			Slot outputSlot = (Slot) slots.get(9);

			if (inputSlot != null && outputSlot != null) {
				ItemStack inputStack = inputSlot.getItem();
				
				if (inputStack.getCount() >= 1) {
					inputStack.shrink(1);
					inputSlot.set(inputStack);

					ItemStack outputStack = outputSlot.getItem();
					if (outputStack.isEmpty()) {
						outputSlot.set(new ItemStack(Coins.IRON_COIN.get(), 64));
					} else if (outputStack.getItem() == Coins.IRON_COIN.get()) {
						// Attention : max stack size est 64. Si on ajoute 64, ça peut déborder.
						// On ajoute ce qu'on peut, le reste est perdu ou dropé ?
						// Ici on suppose que le joueur vide le slot de sortie régulièrement.
						// Mais pour être safe, on ne convertit que si le slot de sortie a de la place.
						if (outputStack.getCount() + 64 <= outputStack.getMaxStackSize()) {
							outputStack.grow(64);
							outputSlot.set(outputStack);
						} else {
							// Pas de place, on annule le retrait
							inputStack.grow(1);
							inputSlot.set(inputStack);
							return; 
						}
					}
					
					player.containerMenu.broadcastChanges();
				}
			}
		}
	}
}
