// src/main/java/fr/renblood/medievalcoins/inventory/banker/ChangeGUIMenu.java
package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.inventory.RestrictedSlotItemHandler;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.procedures.ChangeGUIClosedProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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

	@Override public ItemStack quickMoveStack(Player p, int idx) {
		return ItemStack.EMPTY;
	}

	@Override public void removed(Player player) {
		super.removed(player);
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
