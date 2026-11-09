package fr.renblood.medievalcoins.init;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BlockEntityInit {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MedievalCoin.MODID);

    public static final RegistryObject<BlockEntityType<MerchantCounterBlockEntity>> MERCHANT_COUNTER =
            REGISTRY.register("merchant_counter", () ->
                    BlockEntityType.Builder.of(MerchantCounterBlockEntity::new, BlockInit.MERCHANT_COUNTER.get()).build(null));

    private BlockEntityInit() {
    }
}
