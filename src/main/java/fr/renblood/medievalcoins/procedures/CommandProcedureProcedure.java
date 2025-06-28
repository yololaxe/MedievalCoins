package fr.renblood.medievalcoins.procedures;

import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.network.NetworkHooks;

public class CommandProcedureProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;

		if (entity instanceof ServerPlayer _ent) {
			BlockPos _bpos = BlockPos.containing(x, y, z);

			try {
				NetworkHooks.openScreen(
						_ent,
						new MenuProvider() {
							@Override
							public Component getDisplayName() {
								return Component.translatable("screen.medievalcoins.banker");
							}

							@Override
							public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
								FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
								buf.writeBlockPos(_bpos);
								return new BankerGuiMenu(id, inventory, buf);
							}
						},
						_bpos
				);
			} catch (Exception e) {
				System.err.println("❌ Erreur lors de l'ouverture du menu /bank :");
				e.printStackTrace();
				_ent.sendSystemMessage(Component.literal("§cUne erreur est survenue lors de l'ouverture du menu."));
			}
		}
	}
}
