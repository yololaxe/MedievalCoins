package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import fr.renblood.medievalcoins.market.service.MarketPermissions;

public final class MerchantCounterMenus {
    private MerchantCounterMenus() {
    }

    public static void open(ServerPlayer player, MerchantCounterBlockEntity counter, boolean managementRequested) {
        fr.renblood.medievalcoins.bank.EconomyFeedback.refresh(player,"market:"+counter.getCounterId());
        boolean admin = MarketPermissions.isAdmin(player) && player.isCreative();
        boolean owner = counter.isOwner(player.getUUID());
        Mode mode = managementRequested && owner && counter.isPlayerMarket() ? Mode.OWNER
                : managementRequested && admin ? Mode.ADMIN
                : managementRequested && owner ? Mode.OWNER : Mode.CLIENT;
        MerchantCounterData data = counter.snapshot();
        NetworkHooks.openScreen(player, provider(mode, counter, data), buffer -> {
            buffer.writeBlockPos(counter.getBlockPos());
            data.write(buffer);
        });
    }

    private static MenuProvider provider(Mode mode, MerchantCounterBlockEntity counter, MerchantCounterData data) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(data.name());
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return switch (mode) {
                    case CLIENT -> new MerchantCounterMenu(id, counter.getBlockPos(), data);
                    case OWNER -> new MerchantCounterOwnerMenu(id, inventory, counter, data);
                    case ADMIN -> new MerchantCounterAdminMenu(id, counter.getBlockPos(), data);
                };
            }
        };
    }

    private enum Mode { CLIENT, OWNER, ADMIN }
}
