package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.SubmitWithdrawMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static fr.renblood.medievalcoins.init.MedievalCoinsModMenus.WITHDRAW_MENU;

public class WithdrawGuiMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
    public final Level world;
    public final Player entity;
    public final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ItemStackHandler internal;
    private final Map<Integer, Slot> customSlots = new HashMap<>();

    public WithdrawGuiMenu(int id, Inventory inv, BlockPos pos) {
        super(WITHDRAW_MENU.get(), id);
        this.entity = inv.player;
        this.world  = inv.player.level();
        this.pos    = pos;
        this.access = ContainerLevelAccess.create(world, pos);

        // 12 slots for withdraw options
        this.internal = new ItemStackHandler(12);
        internal.setStackInSlot(0, new ItemStack(Coins.IRON_COIN.get(), 1));
        internal.setStackInSlot(1, new ItemStack(Coins.IRON_COIN.get(), 10));
        internal.setStackInSlot(2, new ItemStack(Coins.IRON_COIN.get(), 32));
        internal.setStackInSlot(3, new ItemStack(Coins.BRONZE_COIN.get(), 1));
        internal.setStackInSlot(4, new ItemStack(Coins.BRONZE_COIN.get(), 10));
        internal.setStackInSlot(5, new ItemStack(Coins.BRONZE_COIN.get(), 32));
        internal.setStackInSlot(6, new ItemStack(Coins.SILVER_COIN.get(), 1));
        internal.setStackInSlot(7, new ItemStack(Coins.SILVER_COIN.get(), 10));
        internal.setStackInSlot(8, new ItemStack(Coins.SILVER_COIN.get(), 32));
        internal.setStackInSlot(9, new ItemStack(Coins.GOLD_COIN.get(), 1));
        internal.setStackInSlot(10, new ItemStack(Coins.GOLD_COIN.get(), 10));
        internal.setStackInSlot(11, new ItemStack(Coins.GOLD_COIN.get(), 32));

        // Add non-interactive slots
        for (int i = 0; i < 12; i++) {
            int slotX = 51 + (i / 3) * 36;
            int slotY = 23 + (i % 3) * 27;
            customSlots.put(i, addSlot(new SlotItemHandler(internal, i, slotX, slotY) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player)    { return false; }
            }));
        }

        // Player inventory (3×9)
        int invX = 33, invY = 109;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        // Hotbar (1×9)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, invX + col * 18, invY + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clicked(int slotIndex, int dragType, ClickType clickType, Player player) {
        super.clicked(slotIndex, dragType, clickType, player);
        if (slotIndex >= 0 && slotIndex < 12) {
            int coinType = slotIndex / 3;
            int amount;
            switch (slotIndex % 3) {
                case 1 -> amount = 10;
                case 2 -> amount = 32;
                default -> amount = 1;
            }
            MedievalCoin.PACKET_HANDLER.sendToServer(
                    new SubmitWithdrawMessage(pos, coinType, amount)
            );
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public Map<Integer, Slot> get() {
        return customSlots;
    }

    public static WithdrawGuiMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new WithdrawGuiMenu(id, inv, pos);
    }
}
