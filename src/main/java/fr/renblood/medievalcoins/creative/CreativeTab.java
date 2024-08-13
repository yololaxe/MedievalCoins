package fr.renblood.medievalcoins.creative;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTab {
    public  static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MedievalCoin.MODID);

    public static final List<Supplier<? extends ItemLike>> MEDIEVAL_TAB_ITEMS = new ArrayList<>();
    public static final RegistryObject<CreativeModeTab> MEDIEVAL_TAB = TABS.register("medieval_coins",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.medieval_coins"))
                    .icon(Coins.GOLD_COIN.get()::getDefaultInstance)
                    .displayItems((itemDisplayParameters, output) -> {
                        MEDIEVAL_TAB_ITEMS.forEach(itemLike -> output.accept(itemLike.get()));
                    })
                    .build()
    );

    public static <T extends Item> RegistryObject<T> addToTab(RegistryObject<T> itemLike){
        MEDIEVAL_TAB_ITEMS.add(itemLike);
        return itemLike;
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(Coins.GOLD_COIN);
        }


    }
}