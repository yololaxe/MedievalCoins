// src/main/java/fr/renblood/medievalcoins/procedures/PageNavigatorProcedure.java
package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.inventory.banker.ChangeGUIMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.network.NetworkHooks;

public class PageNavigatorProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null) return;

		// Ferme d'abord l'ancien container côté client
		if (entity instanceof Player player) {
			player.closeContainer();
		}

		// Puis rouvre le ChangeGUI côté serveur
		if (entity instanceof ServerPlayer serverPlayer) {
			BlockPos pos = BlockPos.containing(x, y, z);
			NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
				@Override
				public Component getDisplayName() {
					return Component.literal("ChangeGUI");
				}

				@Override
				public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
					// ici on passe directement le BlockPos
					return new ChangeGUIMenu(id, inventory, pos);
				}
			}, pos);
		}
	}
}
