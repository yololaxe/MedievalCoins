package fr.renblood.medievalcoins.item;

import fr.renblood.medievalcoins.inventory.purse.PurseContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class Purse extends Item {
    private static final String INVENTORY_TAG = "PurseInventory";
    public Purse(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (!world.isClientSide) {
            ItemStack purseStack = player.getItemInHand(hand);
            MenuProvider containerProvider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("Purse");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player playerEntity) {
                    return new PurseContainer(id, playerInventory, purseStack);
                }
            };

            NetworkHooks.openScreen((ServerPlayer) player, containerProvider, buf -> buf.writeItem(purseStack));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    public SimpleContainer getInventory(ItemStack stack) {
        SimpleContainer inventory = new SimpleContainer(9);
        CompoundTag nbt = stack.getOrCreateTag();
        inventory.fromTag(nbt.getList(INVENTORY_TAG, 10));
        return inventory;
    }

    // Sauvegarde l'inventaire dans l'ItemStack lorsque l'interface est fermée
    public void saveInventory(ItemStack stack, SimpleContainer inventory) {
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.put(INVENTORY_TAG, inventory.createTag());
    }
}
