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



    // Méthode utilitaire pour enregistrer un BlockItem
    private static RegistryObject<Item> block(RegistryObject<Block> block) {
        return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
