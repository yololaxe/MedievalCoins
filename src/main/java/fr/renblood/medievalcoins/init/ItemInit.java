package fr.renblood.medievalcoins.init;


import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import fr.renblood.medievalcoins.creative.CreativeTab;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static fr.renblood.medievalcoins.MedievalCoin.MODID;


public class ItemInit {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // ✅ L’item qui permet de poser le bloc BankDashboardBlock
    public static final RegistryObject<Item> BANK_DASHBOARD =
            CreativeTab.addToTab(REGISTRY.register("bank_dashboard",
                    () -> new BlockItem(BlockInit.BANK_DASHBOARD.get(), new Item.Properties())));

    // Items pour les torches magiques (pas besoin d'être dans le creative tab car item technique)
    public static final RegistryObject<Item> MAGIC_TORCH = REGISTRY.register("magic_torch",
            () -> new BlockItem(BlockInit.MAGIC_TORCH.get(), new Item.Properties()));

    public static final RegistryObject<Item> PRAYER_LECTERN =
            CreativeTab.addToTab(REGISTRY.register("prayer_lectern",
                    () -> new BlockItem(BlockInit.PRAYER_LECTERN.get(), new Item.Properties())));

    public static final RegistryObject<Item> MERCHANT_COUNTER =
            CreativeTab.addToTab(REGISTRY.register("merchant_counter",
                    () -> new BlockItem(BlockInit.MERCHANT_COUNTER.get(), new Item.Properties())));

            
    // Item de verrouillage supprimé

    // Méthode utilitaire pour enregistrer un BlockItem
    private static RegistryObject<Item> block(RegistryObject<Block> block) {
        return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
