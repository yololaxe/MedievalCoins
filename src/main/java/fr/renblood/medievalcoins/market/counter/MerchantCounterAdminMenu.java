package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public class MerchantCounterAdminMenu extends AbstractMerchantCounterMenu {
    public MerchantCounterAdminMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, buffer.readBlockPos(), MerchantCounterData.read(buffer));
    }

    public MerchantCounterAdminMenu(int id, BlockPos pos, MerchantCounterData data) {
        super(MedievalCoinsModMenus.MERCHANT_COUNTER_ADMIN.get(), id, pos, data);
    }
}
