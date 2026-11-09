package fr.renblood.medievalcoins.init;

import fr.renblood.medievalcoins.block.BankDashboardBlock;
import fr.renblood.medievalcoins.block.MagicTorchBlock;
import fr.renblood.medievalcoins.block.MagicWallTorchBlock;
import fr.renblood.medievalcoins.block.PrayerLecternBlock;
import fr.renblood.medievalcoins.block.MerchantCounterBlock;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.Block;

import static fr.renblood.medievalcoins.MedievalCoin.MODID;


public class BlockInit {
    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final java.util.Map<String, RegistryObject<Block>> HOUSE_DECORATIONS = new java.util.LinkedHashMap<>();
    static {
        HOUSE_DECORATIONS.put("renblood_royal_seal", REGISTRY.register("renblood_royal_seal",
                () -> new fr.renblood.medievalcoins.block.MonumentalStandardBlock(true, 2, 2)));
        HOUSE_DECORATIONS.put("renblood_royal_pedestal", REGISTRY.register("renblood_royal_pedestal",
                () -> new fr.renblood.medievalcoins.block.HouseStandardBlock(false)));
        for (String house : new String[]{"feran", "goodbrook", "linster", "rok", "rollingford", "roxton", "wyne", "banefort", "chalk"}) {
            HOUSE_DECORATIONS.put(house + "_long_banner", REGISTRY.register(house + "_long_banner",
                    () -> new fr.renblood.medievalcoins.block.MonumentalStandardBlock(true, 1, 2)));
            HOUSE_DECORATIONS.put(house + "_grand_banner", REGISTRY.register(house + "_grand_banner",
                    () -> new fr.renblood.medievalcoins.block.MonumentalStandardBlock(true)));
            HOUSE_DECORATIONS.put(house + "_tower_flag", REGISTRY.register(house + "_tower_flag",
                    () -> new fr.renblood.medievalcoins.block.MonumentalStandardBlock(false)));
            HOUSE_DECORATIONS.put(house + "_banner", REGISTRY.register(house + "_banner",
                    () -> new fr.renblood.medievalcoins.block.HouseStandardBlock(true)));
            HOUSE_DECORATIONS.put(house + "_flag", REGISTRY.register(house + "_flag",
                    () -> new fr.renblood.medievalcoins.block.HouseStandardBlock(false)));
        }
    }
    public static final RegistryObject<Block> BANK_DASHBOARD = REGISTRY.register("bank_dashboard", BankDashboardBlock::new);
    
    public static final RegistryObject<Block> MAGIC_TORCH = REGISTRY.register("magic_torch", MagicTorchBlock::new);
    public static final RegistryObject<Block> MAGIC_WALL_TORCH = REGISTRY.register("magic_wall_torch", MagicWallTorchBlock::new);
    public static final RegistryObject<Block> PRAYER_LECTERN = REGISTRY.register("prayer_lectern", PrayerLecternBlock::new);
    public static final RegistryObject<Block> MERCHANT_COUNTER = REGISTRY.register("merchant_counter", MerchantCounterBlock::new);
}
