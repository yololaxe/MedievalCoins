package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.init.MedievalCoinsModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public class MerchantCounterMenu extends AbstractMerchantCounterMenu {
    public MerchantCounterMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, buffer.readBlockPos(), MerchantCounterData.read(buffer));
    }

    public MerchantCounterMenu(int id, BlockPos pos, MerchantCounterData data) {
        super(MedievalCoinsModMenus.MERCHANT_COUNTER.get(), id, pos, data);
    }
}
