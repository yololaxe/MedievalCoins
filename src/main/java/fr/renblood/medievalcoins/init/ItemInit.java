package fr.renblood.medievalcoins.init;


import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Block;
import fr.renblood.medievalcoins.creative.CreativeTab;
import fr.renblood.medievalcoins.init.SoundInit;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static fr.renblood.medievalcoins.MedievalCoin.MODID;


public class ItemInit {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    static {
        HouseEquipmentInit.register();
        BlockInit.HOUSE_DECORATIONS.forEach((id, block) ->
                CreativeTab.addToHeraldry(REGISTRY.register(id, () -> new BlockItem(block.get(), new Item.Properties()))));
    }

    // ✅ L’item qui permet de poser le bloc BankDashboardBlock
    public static final RegistryObject<Item> BANK_DASHBOARD =
            CreativeTab.addToTab(REGISTRY.register("bank_dashboard",
                    () -> new BlockItem(BlockInit.BANK_DASHBOARD.get(), new Item.Properties())));

    // Also exposed in the general creative tab; the wall variant shares this item.
    public static final RegistryObject<Item> MAGIC_TORCH = REGISTRY.register("magic_torch",
            () -> new BlockItem(BlockInit.MAGIC_TORCH.get(), new Item.Properties()));

    public static final RegistryObject<Item> PRAYER_LECTERN =
            CreativeTab.addToTab(REGISTRY.register("prayer_lectern",
                    () -> new BlockItem(BlockInit.PRAYER_LECTERN.get(), new Item.Properties())));

    public static final RegistryObject<Item> MERCHANT_COUNTER =
            CreativeTab.addToTab(REGISTRY.register("merchant_counter",
                    () -> new BlockItem(BlockInit.MERCHANT_COUNTER.get(), new Item.Properties())));

            
    public static final RegistryObject<Item> PIPPIN_MUSIC_DISC =
            CreativeTab.addToTab(REGISTRY.register("pippin_music_disc",
                    () -> new RecordItem(15, SoundInit.PIPPIN, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON), fr.renblood.medievalcoins.music.MusicCatalog.LENGTH_TICKS[1])));
    public static final java.util.Map<Integer,RegistryObject<Item>> MUSIC_DISCS = fr.renblood.medievalcoins.music.MusicDiscs.register(REGISTRY,PIPPIN_MUSIC_DISC);
    // Item de verrouillage supprimé

    // Méthode utilitaire pour enregistrer un BlockItem
    private static RegistryObject<Item> block(RegistryObject<Block> block) {
        return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
