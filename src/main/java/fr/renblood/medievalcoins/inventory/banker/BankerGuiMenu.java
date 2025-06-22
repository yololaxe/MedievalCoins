package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BankerGuiMenu extends AbstractContainerMenu {

	public static final Map<String, Object> guistate = new HashMap<>();
	final Level world;
	final Player entity;
	final int x;
    final int y;
    final int z;
	private final ContainerLevelAccess access;
	private final IItemHandler internal;
	private final Map<Integer, Slot> customSlots = new HashMap<>();

	/**
	 * Constructeur unique pour Forge (appelé à la fois serveur & client via IForgeMenuType.create)
	 */
	public BankerGuiMenu(int id, Inventory inv, FriendlyByteBuf buf) {
		super(MedievalCoinsModMenus.BANKER_GUI.get(), id);
		this.entity = inv.player;
		this.world  = inv.player.level();

		// on crée un handler vide (pas de "poche" interne par défaut)
		this.internal = new ItemStackHandler(0);

		// on lit le BlockPos du buf (côté serveur on l'a écrit dans openScreen)
		BlockPos pos = buf.readBlockPos();
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.access = ContainerLevelAccess.create(world, pos);

		// === ICI POSE TES SLOTS ===
		// Exemple : 4 slots “pièces” en haut
		int startX = 62, startY = 17;
		for (int i = 0; i < 4; i++) {
			int slotIndex = i;
			int slotX = startX + i * 18;
			int slotY = startY;
			Slot s = new Slot((Container)internal, slotIndex, slotX, slotY);
			this.addSlot(s);
			this.customSlots.put(slotIndex, s);
		}

		// Inventaire joueur (3×9)
		int invX = 8, invY = 84;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9 + 9;
				this.addSlot(new Slot(inv, index,
						invX + col * 18,
						invY + row * 18));
			}
		}
		// Hotbar (1×9)
		int hotbarY = invY + 58;
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inv, col,
					invX + col * 18, hotbarY));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		// tu peux remplacer par un check de proximité si tu veux
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		// désactivé pour l’instant
		return ItemStack.EMPTY;
	}

	/** Exposition des slots custom si besoin ailleurs */
	public Map<Integer, Slot> getCustomSlots() {
		return this.customSlots;
	}
}
