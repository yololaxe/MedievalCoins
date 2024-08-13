package fr.renblood.medievalcoins.item;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.init.EntityInit;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static fr.renblood.medievalcoins.creative.CreativeTab.addToTab;

public class Coins {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MedievalCoin.MODID);
    public static final RegistryObject<Item> BRONZE_COIN = addToTab(ITEMS.register("bronze_coin", () -> new Item(new Item.Properties())));
    public static final RegistryObject<Item> SILVER_COIN = addToTab(ITEMS.register("silver_coin", () -> new Item(new Item.Properties())));
    public static final RegistryObject<Item> GOLD_COIN = addToTab(ITEMS.register("gold_coin", () -> new Item(new Item.Properties())));
    public static final RegistryObject<Item> PURSE = addToTab(ITEMS.register("purse",
            () -> new Purse(new Item.Properties().stacksTo(1))));

    public static final RegistryObject<ForgeSpawnEggItem> BANKER_SPAWN__EGG = addToTab(ITEMS.register("banker_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityInit.BANKER, 0xF0ABD1, 0xAE4C82, new Item.Properties() )));


    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
