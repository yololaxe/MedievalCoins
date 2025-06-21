package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import fr.renblood.medievalcoins.inventory.RestrictedSlotItemHandler;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.procedures.ChangeGUIClosedProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ChangeGUIMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
	public final static HashMap<String, Object> guistate = new HashMap<>();
	public final Level world;
	public final Player entity;
	public int x, y, z;
	private ContainerLevelAccess access = ContainerLevelAccess.NULL;
	private IItemHandler internal;
	private final Map<Integer, Slot> customSlots = new HashMap<>();
	private boolean bound = false;
	private Supplier<Boolean> boundItemMatcher = null;
	private Entity boundEntity = null;
	private BlockEntity boundBlockEntity = null;

	public ChangeGUIMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(MedievalCoinsModMenus.CHANGE_GUI.get(), id);
		this.entity = inv.player;
		this.world = inv.player.level();
		this.internal = new ItemStackHandler(12);
		BlockPos pos = null;
		if (extraData != null) {
			pos = extraData.readBlockPos();
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
			access = ContainerLevelAccess.create(world, pos);
		}
		if (pos != null) {
			if (extraData.readableBytes() == 1) { // bound to item
				byte hand = extraData.readByte();
				ItemStack itemstack = hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem();
				this.boundItemMatcher = () -> itemstack == (hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem());
				itemstack.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
					this.internal = capability;
					this.bound = true;
				});
			} else if (extraData.readableBytes() > 1) { // bound to entity
				extraData.readByte(); // drop padding
				boundEntity = world.getEntity(extraData.readVarInt());
				if (boundEntity != null)
					boundEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
						this.internal = capability;
						this.bound = true;
					});
			} else { // might be bound to block
				boundBlockEntity = this.world.getBlockEntity(pos);
				if (boundBlockEntity != null)
					boundBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(capability -> {
						this.internal = capability;
						this.bound = true;
					});
			}
		}

		Set<Item> allowedItemsCopper = new HashSet<>();
		allowedItemsCopper.add(Items.COPPER_INGOT);
		Set<Item> allowedItemsBronze = new HashSet<>();
		allowedItemsBronze.add(Coins.BRONZE_COIN.get());
		Set<Item> allowedItemsSilver = new HashSet<>();
		allowedItemsSilver.add(Coins.SILVER_COIN.get());
		Set<Item> allowedItemsGold = new HashSet<>();
		allowedItemsGold.add(Coins.GOLD_COIN.get());

		this.customSlots.put(0, this.addSlot(new RestrictedSlotItemHandler(internal, 0, 22, 12, allowedItemsCopper) {
			private final int slot = 0;
		}));
		this.customSlots.put(1, this.addSlot(new RestrictedSlotItemHandler(internal, 1, 22, 45, allowedItemsBronze) {
			private final int slot = 1;
		}));
		this.customSlots.put(2, this.addSlot(new RestrictedSlotItemHandler(internal, 2, 22, 78, allowedItemsSilver) {
			private final int slot = 2;
		}));
		this.customSlots.put(3, this.addSlot(new RestrictedSlotItemHandler(internal, 3, 82, 12, allowedItemsBronze) {
			private final int slot = 3;

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		}));
		this.customSlots.put(4, this.addSlot(new RestrictedSlotItemHandler(internal, 4, 82, 45, allowedItemsSilver) {
			private final int slot = 4;

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		}));
		this.customSlots.put(5, this.addSlot(new RestrictedSlotItemHandler(internal, 5, 82, 78, allowedItemsGold) {
			private final int slot = 5;

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		}));
		this.customSlots.put(6, this.addSlot(new RestrictedSlotItemHandler(internal, 6, 129, 12, allowedItemsBronze) {
			private final int slot = 6;
		}));
		this.customSlots.put(7, this.addSlot(new RestrictedSlotItemHandler(internal, 7, 129, 45, allowedItemsSilver) {
			private final int slot = 7;
		}));
		this.customSlots.put(8, this.addSlot(new RestrictedSlotItemHandler(internal, 8, 129, 78, allowedItemsGold) {
			private final int slot = 8;
		}));
		this.customSlots.put(9, this.addSlot(new RestrictedSlotItemHandler(internal, 9, 188, 12, allowedItemsCopper) {
			private final int slot = 9;

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		}));
		this.customSlots.put(10, this.addSlot(new RestrictedSlotItemHandler(internal, 10, 188, 45, allowedItemsBronze) {
			private final int slot = 10;

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		}));
		this.customSlots.put(11, this.addSlot(new RestrictedSlotItemHandler(internal, 11, 188, 78, allowedItemsSilver) {
			private final int slot = 11;

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		}));
		for (int si = 0; si < 3; ++si)
			for (int sj = 0; sj < 9; ++sj)
				this.addSlot(new Slot(inv, sj + (si + 1) * 9, 25 + 8 + sj * 18, 25 + 84 + si * 18));
		for (int si = 0; si < 9; ++si)
			this.addSlot(new Slot(inv, si, 25 + 8 + si * 18, 25 + 142));
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.bound) {
			if (this.boundItemMatcher != null)
				return this.boundItemMatcher.get();
			else if (this.boundBlockEntity != null)
				return AbstractContainerMenu.stillValid(this.access, player, this.boundBlockEntity.getBlockState().getBlock());
			else if (this.boundEntity != null)
				return this.boundEntity.isAlive();
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = (Slot) this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			if (index < 12) {
				if (!this.moveItemStackTo(itemstack1, 12, this.slots.size(), true))
					return ItemStack.EMPTY;
				slot.onQuickCraft(itemstack1, itemstack);
			} else if (!this.moveItemStackTo(itemstack1, 0, 12, false)) {
				if (index < 12 + 27) {
					if (!this.moveItemStackTo(itemstack1, 12 + 27, this.slots.size(), true))
						return ItemStack.EMPTY;
				} else {
					if (!this.moveItemStackTo(itemstack1, 12, 12 + 27, false))
						return ItemStack.EMPTY;
				}
				return ItemStack.EMPTY;
			}
			if (itemstack1.getCount() == 0)
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
	protected boolean moveItemStackTo(ItemStack p_38904_, int p_38905_, int p_38906_, boolean p_38907_) {
		boolean flag = false;
		int i = p_38905_;
		if (p_38907_) {
			i = p_38906_ - 1;
		}
		if (p_38904_.isStackable()) {
			while (!p_38904_.isEmpty()) {
				if (p_38907_) {
					if (i < p_38905_) {
						break;
					}
				} else if (i >= p_38906_) {
					break;
				}
				Slot slot = this.slots.get(i);
				ItemStack itemstack = slot.getItem();
				if (slot.mayPlace(itemstack) && !itemstack.isEmpty() && ItemStack.isSameItemSameTags(p_38904_, itemstack)) {
					int j = itemstack.getCount() + p_38904_.getCount();
					int maxSize = Math.min(slot.getMaxStackSize(), p_38904_.getMaxStackSize());
					if (j <= maxSize) {
						p_38904_.setCount(0);
						itemstack.setCount(j);
						slot.set(itemstack);
						flag = true;
					} else if (itemstack.getCount() < maxSize) {
						p_38904_.shrink(maxSize - itemstack.getCount());
						itemstack.setCount(maxSize);
						slot.set(itemstack);
						flag = true;
					}
				}
				if (p_38907_) {
					--i;
				} else {
					++i;
				}
			}
		}
		if (!p_38904_.isEmpty()) {
			if (p_38907_) {
				i = p_38906_ - 1;
			} else {
				i = p_38905_;
			}
			while (true) {
				if (p_38907_) {
					if (i < p_38905_) {
						break;
					}
				} else if (i >= p_38906_) {
					break;
				}
				Slot slot1 = this.slots.get(i);
				ItemStack itemstack1 = slot1.getItem();
				if (itemstack1.isEmpty() && slot1.mayPlace(p_38904_)) {
					if (p_38904_.getCount() > slot1.getMaxStackSize()) {
						slot1.setByPlayer(p_38904_.split(slot1.getMaxStackSize()));
					} else {
						slot1.setByPlayer(p_38904_.split(p_38904_.getCount()));
					}
					slot1.setChanged();
					flag = true;
					break;
				}
				if (p_38907_) {
					--i;
				} else {
					++i;
				}
			}
		}
		return flag;
	}
	private static final Logger LOGGER = LogManager.getLogger();
	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);

		// Execute the procedure to drop items on the ground
		ChangeGUIClosedProcedure.execute(world, playerIn);

		// Handle bound inventories: drop items if the player is no longer alive or disconnected
		if (!bound && playerIn instanceof ServerPlayer serverPlayer) {
			if (!serverPlayer.isAlive() || serverPlayer.hasDisconnected()) {
				for (int j = 0; j < internal.getSlots(); ++j) {
					playerIn.drop(internal.extractItem(j, internal.getStackInSlot(j).getCount(), false), false);
				}
			} else {
				for (int i = 0; i < internal.getSlots(); ++i) {
					playerIn.getInventory().placeItemBackInInventory(internal.extractItem(i, internal.getStackInSlot(i).getCount(), false));
				}
			}
		}
		if (!bound && playerIn instanceof ServerPlayer serverPlayer) {

		}
		for (int i = 0; i < internal.getSlots(); ++i) {
			ItemStack stack = internal.getStackInSlot(i);
			if (!stack.isEmpty()) {
				// Tant qu'il reste des items dans le stack
				while (!stack.isEmpty()) {
					// Essayez d'ajouter autant que possible à l'inventaire du joueur
					boolean added = playerIn.getInventory().add(stack);

					if (!added) {
						// Si l'ajout échoue, drop l'item restant au sol
						playerIn.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
								playerIn.level(), playerIn.getX(), playerIn.getY(), playerIn.getZ(), stack
						));
						// Vider le stack car tout a été dropé
						stack.setCount(0);
					}
				}
			}
		}
	}

	@Override
	public Map<Integer, Slot> get() {
		return null;
	}
}
