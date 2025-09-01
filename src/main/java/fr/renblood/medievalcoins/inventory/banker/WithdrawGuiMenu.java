package fr.renblood.medievalcoins.inventory.banker;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import fr.renblood.medievalcoins.network.SubmitWithdrawMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static fr.renblood.medievalcoins.init.MedievalCoinsModMenus.WITHDRAW_MENU;

public class WithdrawGuiMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
    public final Player entity;
    public final BlockPos pos;
    private final ItemStackHandler internal;
    private final Map<Integer, Slot> customSlots = new HashMap<>();

    public WithdrawGuiMenu(int id, Inventory inv, BlockPos pos) {
        super(WITHDRAW_MENU.get(), id);
        this.entity = inv.player;
        this.pos    = pos;

        // 12 boutons de retrait
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

        // Ajout des slots (lecture seule) dans la GUI
        for (int i = 0; i < 12; i++) {
            int x = 51 + (i / 3) * 36;
            int y = 23 + (i % 3) * 27;
            customSlots.put(i, addSlot(new SlotItemHandler(internal, i, x, y) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p)    { return false; }
            }));
        }

        // Inventaire joueur (3×9 + hotbar)
        int invX = 33, invY = 109;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, invX + col * 18, invY + 58));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clicked(int slotIndex, int dragType, ClickType clickType, Player player) {
        super.clicked(slotIndex, dragType, clickType, player);

        // Seulement pour PICKUP (clic normal) sur un bouton 0..11
        if (clickType == ClickType.PICKUP && slotIndex >= 0 && slotIndex < 12) {
            // détermination du type de pièce et de la quantité
            int coinType = slotIndex / 3;
            int amount;
            switch (slotIndex % 3) {
                case 1 -> amount = 10;
                case 2 -> amount = 32;
                default -> amount = 1;
            }

            // prépare la pile à donner
            var item = switch (coinType) {
                case 0 -> Coins.IRON_COIN.get();
                case 1 -> Coins.BRONZE_COIN.get();
                case 2 -> Coins.SILVER_COIN.get();
                default -> Coins.GOLD_COIN.get();
            };
            ItemStack toGive = new ItemStack(item, amount);

            // vérification serveur-side se fera dans le handler, mais on peut checker client-side
            // on envoie le packet quoi qu'il arrive, le serveur bloquera si inventaire plein
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
