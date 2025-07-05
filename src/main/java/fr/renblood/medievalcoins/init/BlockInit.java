package fr.renblood.medievalcoins.init;




import fr.renblood.medievalcoins.block.BankDashboardBlock;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.Block;

import static fr.renblood.medievalcoins.MedievalCoin.MODID;


public class BlockInit {
    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final RegistryObject<Block> BANK_DASHBOARD = REGISTRY.register("bank_dashboard", BankDashboardBlock::new);
}
