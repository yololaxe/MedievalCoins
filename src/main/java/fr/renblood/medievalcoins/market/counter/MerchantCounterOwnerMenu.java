package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class MerchantCounterOwnerMenu extends AbstractMerchantCounterMenu {
    private static final int PLAYER_MARKET_SLOT_COUNT = MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS;
    private static final int STOCK_GRID_X = 259;
    private static final int STOCK_GRID_Y = 68;
    private static final int STOCK_GRID_SPACING = 18;
    private final boolean playerMarket;
    private final ItemStackHandler clientHandler;

    public MerchantCounterOwnerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), MerchantCounterData.read(buffer));
    }

    public MerchantCounterOwnerMenu(int id, Inventory inventory, BlockPos pos, MerchantCounterData data) {
        super(MedievalCoinsModMenus.MERCHANT_COUNTER_OWNER.get(), id, pos, data);
        this.playerMarket = "PLAYER_MARKET".equals(data.type());
        this.clientHandler = createClientHandler(data);
        if (playerMarket) addPlayerMarketSlots(inventory.player, data);
        addPlayerInventory(inventory);
    }

    public MerchantCounterOwnerMenu(int id, Inventory inventory, MerchantCounterBlockEntity counter, MerchantCounterData data) {
        super(MedievalCoinsModMenus.MERCHANT_COUNTER_OWNER.get(), id, counter.getBlockPos(), data);
        this.playerMarket = counter.isPlayerMarket();
        this.clientHandler = null;
        if (playerMarket) addServerSlots(inventory.player, counter);
        addPlayerInventory(inventory);
    }

    public boolean isPlayerMarket() {
        return playerMarket;
    }

    public int playerMarketSlots() {
        return playerMarket ? PLAYER_MARKET_SLOT_COUNT : 0;
    }

    private void addServerSlots(Player owner, MerchantCounterBlockEntity counter) {
        ItemStackHandler display=new ItemStackHandler(PLAYER_MARKET_SLOT_COUNT);
        for(int slot=0;slot<PLAYER_MARKET_SLOT_COUNT;slot++){
            ItemStack stack=counter.playerMarketStack(slot).copy();
            if(counter.isCommerceStockMirror())stack.setCount(counter.getCommerceStock(slot));display.setStackInSlot(slot,stack);
        }
        for (int slot = 0; slot < PLAYER_MARKET_SLOT_COUNT; slot++) {
            addSlot(new SlotItemHandler(display, slot,
                    STOCK_GRID_X + (slot % 9) * STOCK_GRID_SPACING,
                    STOCK_GRID_Y + (slot / 9) * STOCK_GRID_SPACING) {
                @Override
                public boolean mayPlace(ItemStack stack) {return false;}
                @Override public boolean mayPickup(Player player){return false;}
            });
        }
    }

    private void addPlayerMarketSlots(Player player, MerchantCounterData data) {
        for (int slot = 0; slot < PLAYER_MARKET_SLOT_COUNT; slot++) {
            addSlot(new SlotItemHandler(clientHandler, slot,
                    STOCK_GRID_X + (slot % 9) * STOCK_GRID_SPACING,
                    STOCK_GRID_Y + (slot / 9) * STOCK_GRID_SPACING) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player owner) {
                    return false;
                }

                @Override
                public int getMaxStackSize() {
                    return 64;
                }
            });
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        int invX = 259;
        int invY = 205;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, invX + col * 18, invY + 58));
        }
    }

    private static ItemStackHandler createClientHandler(MerchantCounterData data) {
        ItemStackHandler handler = new ItemStackHandler(PLAYER_MARKET_SLOT_COUNT);
        if (!"PLAYER_MARKET".equals(data.type())) return handler;
        for (PlayerMarketSlotData slot : data.playerMarketSlots()) {
            if (slot.slot() >= 0 && slot.slot() < PLAYER_MARKET_SLOT_COUNT) {
                handler.setStackInSlot(slot.slot(), slot.stack().copy());
            }
        }
        return handler;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
