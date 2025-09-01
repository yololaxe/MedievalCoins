// src/main/java/fr/renblood/medievalcoins/inventory/banker/ChangeGUIMenu.java
package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.inventory.RestrictedSlotItemHandler;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.procedures.ChangeGUIClosedProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ChangeGUIMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
	public static final HashMap<String, Object> guistate = new HashMap<>();
	public final Level world;
	public final Player entity;
	public final BlockPos pos;
	private final ContainerLevelAccess access;
	private final ItemStackHandler internal;
	private final Map<Integer, Slot> customSlots = new HashMap<>();

	public ChangeGUIMenu(int id, Inventory inv, BlockPos pos) {
		super(MedievalCoinsModMenus.CHANGE_GUI.get(), id);
		this.entity = inv.player;
		this.world  = inv.player.level();
		this.pos    = pos;
		this.access = ContainerLevelAccess.create(world, pos);

		// 1) On crée le handler à 12 slots
		this.internal = new ItemStackHandler(12);

		// 2) On pré-remplit les slots d’« input » pour chaque conversion
		//    (permet d’avoir directement 64 unités à transformer)
//		internal.setStackInSlot(0,  new ItemStack(Items.IRON_INGOT,   64)); // fer → bronze
//		internal.setStackInSlot(1,  new ItemStack(Coins.BRONZE_COIN.get(), 64)); // bronze → argent
//		internal.setStackInSlot(2,  new ItemStack(Coins.SILVER_COIN.get(), 64)); // argent → or
//		internal.setStackInSlot(6,  new ItemStack(Coins.BRONZE_COIN.get(), 64)); // bronze ← fer
//		internal.setStackInSlot(7,  new ItemStack(Coins.SILVER_COIN.get(), 64)); // argent ← bronze
//		internal.setStackInSlot(8,  new ItemStack(Coins.GOLD_COIN.get(),   64)); // or ← argent

		// 3) Définition des sets autorisés par slot
		Set<Item> ironSet   = Set.of(Coins.IRON_COIN.get());
		Set<Item> bronzeSet = Set.of(Coins.BRONZE_COIN.get());
		Set<Item> silverSet = Set.of(Coins.SILVER_COIN.get());
		Set<Item> goldSet   = Set.of(Coins.GOLD_COIN.get());

		// 4) Création des 12 slots « custom »
		//    indices 0–2 = inputs pour montée de valeur
		customSlots.put(0, addSlot(new RestrictedSlotItemHandler(internal,  0,  22,  12, ironSet)));
		customSlots.put(1, addSlot(new RestrictedSlotItemHandler(internal,  1,  22,  45, bronzeSet)));
		customSlots.put(2, addSlot(new RestrictedSlotItemHandler(internal,  2,  22,  78, silverSet)));
		//    indices 3–5 = outputs pour montée (lecture seule)
		customSlots.put(3, addSlot(new RestrictedSlotItemHandler(internal,  3,  82,  12, bronzeSet) {
			@Override public boolean mayPlace(ItemStack s){ return false; }
		}));
		customSlots.put(4, addSlot(new RestrictedSlotItemHandler(internal,  4,  82,  45, silverSet) {
			@Override public boolean mayPlace(ItemStack s){ return false; }
		}));
		customSlots.put(5, addSlot(new RestrictedSlotItemHandler(internal,  5,  82,  78, goldSet) {
			@Override public boolean mayPlace(ItemStack s){ return false; }
		}));
		//    indices 6–8 = inputs pour descente de valeur
		customSlots.put(6, addSlot(new RestrictedSlotItemHandler(internal,  6, 129,  12, bronzeSet)));
		customSlots.put(7, addSlot(new RestrictedSlotItemHandler(internal,  7, 129,  45, silverSet)));
		customSlots.put(8, addSlot(new RestrictedSlotItemHandler(internal,  8, 129,  78, goldSet)));
		//    indices 9–11 = outputs pour descente (lecture seule)
		customSlots.put(9, addSlot(new RestrictedSlotItemHandler(internal,  9, 188,  12, ironSet) {
			@Override public boolean mayPlace(ItemStack s){ return false; }
		}));
		customSlots.put(10,addSlot(new RestrictedSlotItemHandler(internal, 10,188,  45, bronzeSet) {
			@Override public boolean mayPlace(ItemStack s){ return false; }
		}));
		customSlots.put(11,addSlot(new RestrictedSlotItemHandler(internal, 11,188,  78, silverSet) {
			@Override public boolean mayPlace(ItemStack s){ return false; }
		}));

		// 5) Slots inventaire joueur (3×9 + hotbar)
		int invX = 25 + 8, invY = 25 + 84;
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				addSlot(new Slot(inv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
		int hotbarY = 25 + 142;
		for (int col = 0; col < 9; col++)
			addSlot(new Slot(inv, col, invX + col * 18, hotbarY));
	}

	/** Côté réseau : reconstitue le BlockPos et appelle notre constructeur */
	public static ChangeGUIMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		return new ChangeGUIMenu(id, inv, pos);
	}

	@Override public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(access, player, world.getBlockState(pos).getBlock());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) return original;

		ItemStack stackInSlot = slot.getItem();
		original = stackInSlot.copy();

		// bornes
		int CUSTOM_START = 0, CUSTOM_END = 12;      // slots custom 0..11
		int PLAYER_START = 12, PLAYER_END = 12 + 36; // slots joueur 12..47

		// Si on Shift-clic depuis un slot custom
		if (index < CUSTOM_END) {
			// on essaye de renvoyer dans l’inventaire joueur
			if (!this.moveItemStackTo(stackInSlot, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stackInSlot, original);

		} else {
			// on vient de l'inventaire joueur
			boolean moved = false;

			// montée de valeur
			if (stackInSlot.getItem() == Coins.IRON_COIN.get()) {
				moved = this.moveItemStackTo(stackInSlot, 0, 1, false);
			} else if (stackInSlot.getItem() == Coins.BRONZE_COIN.get()) {
				moved = this.moveItemStackTo(stackInSlot, 1, 2, false)
						|| this.moveItemStackTo(stackInSlot, 6, 7, false); // on peut aussi descendre
			} else if (stackInSlot.getItem() == Coins.SILVER_COIN.get()) {
				moved = this.moveItemStackTo(stackInSlot, 2, 3, false)
						|| this.moveItemStackTo(stackInSlot, 7, 8, false);
			} else if (stackInSlot.getItem() == Coins.GOLD_COIN.get()) {
				moved = this.moveItemStackTo(stackInSlot, 8, 9, false);
			}

			// si pas encore déplacé, on bascule main <-> hotbar
			if (!moved) {
				int invMainEnd = PLAYER_START + 27; // 12..38 = main, 39..47 = hotbar
				if (index < invMainEnd) {
					moved = this.moveItemStackTo(stackInSlot, invMainEnd, PLAYER_END, false);
				} else {
					moved = this.moveItemStackTo(stackInSlot, PLAYER_START, invMainEnd, false);
				}
			}

			if (!moved) return ItemStack.EMPTY;
		}

		// nettoyage du slot d’origine
		if (stackInSlot.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}


	@Override
	public void removed(Player player) {
		super.removed(player);

		// calcul du point devant le joueur
		Direction look = player.getDirection();
		double offsetX = look.getStepX() * 0.5;
		double offsetZ = look.getStepZ() * 0.5;
		double spawnX = player.getX() + offsetX;
		double spawnY = player.getY() + 1.0;
		double spawnZ = player.getZ() + offsetZ;

		// 1) Pour ChangeGUIMenu : vider internal
		for (int i = 0; i < this.internal.getSlots(); i++) {
			ItemStack stack = this.internal.extractItem(i, Integer.MAX_VALUE, false);
			if (!stack.isEmpty()) {
				if (!player.getInventory().add(stack)) {
					ItemEntity dropped = new ItemEntity(player.level(), spawnX, spawnY, spawnZ, stack);
					player.level().addFreshEntity(dropped);
				}
			}
		}

		// 2) Procédure existante
		ChangeGUIClosedProcedure.execute(world, player);
	}



	@Override public Map<Integer, Slot> get() {
		return customSlots;
	}

	/** Pour d’éventuels usages externes */
	public IItemHandler getInternal() {
		return internal;
	}
}
