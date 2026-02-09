package fr.renblood.medievalcoins.inventory.banker;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;

import java.util.function.Supplier;
import java.util.Map;
import java.util.HashMap;

import static fr.renblood.medievalcoins.init.MedievalCoinsModMenus.BANKER_GUI;

public class BankerGuiMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
	public final static HashMap<String, Object> guistate = new HashMap<>();
	public final Level world;
	public final Player entity;
	public int x, y, z;
	private final ContainerLevelAccess access;
	private final Map<Integer, Slot> customSlots = new HashMap<>();

	public BankerGuiMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(BANKER_GUI.get(), id);
		this.entity = inv.player;
		this.world = inv.player.level();

		BlockPos pos = extraData.readBlockPos();
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.access = ContainerLevelAccess.create(world, pos);

		// ✅ Ajout de l’inventaire du joueur (3x9)
		// 📐 Placement de l'inventaire joueur (3 lignes + hotbar)
		int baseX = 25 + 8;
		int baseY = 25 + 84;

// Lignes d'inventaire (3x9)
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(inv, col + row * 9 + 9, baseX + col * 18, baseY + row * 18));
			}
		}

// Hotbar (1x9)
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(inv, col, baseX + col * 18, baseY + 58));
		}

	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(this.access, player, this.world.getBlockState(BlockPos.containing(x, y, z)).getBlock());
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			
			// Dans ce menu, il n'y a QUE l'inventaire du joueur (pas de slots de coffre).
			// Donc le shift-click sert juste à déplacer entre Hotbar et Main Inventory.
			
			if (index < 27) { // Main Inventory (0-26) -> Hotbar (27-35)
				if (!this.moveItemStackTo(itemstack1, 27, 36, false)) {
					return ItemStack.EMPTY;
				}
			} else if (index < 36) { // Hotbar (27-35) -> Main Inventory (0-26)
				if (!this.moveItemStackTo(itemstack1, 0, 27, false)) {
					return ItemStack.EMPTY;
				}
			}

			if (itemstack1.isEmpty())
				slot.set(ItemStack.EMPTY);
			else
				slot.setChanged();
			if (itemstack1.getCount() == itemstack.getCount())
				return ItemStack.EMPTY;
			slot.onTake(playerIn, itemstack1);
		}
		return itemstack;
	}

	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);
	}

	@Override
	public Map<Integer, Slot> get() {
		return customSlots;
	}
}
