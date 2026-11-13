package fr.renblood.medievalcoins.creative;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.init.BlockInit;
import fr.renblood.medievalcoins.init.ItemInit;
import fr.renblood.medievalcoins.item.Coins;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MedievalCoin.MODID);

    public static final List<Supplier<? extends ItemLike>> MEDIEVAL_TAB_ITEMS = new ArrayList<>();
    private static final List<RegistryObject<? extends Item>> HERALDRY_ITEMS = new ArrayList<>();
    private static final List<String> HERALDRY_ORDER = List.of(
            "sigil", "banner", "long_banner", "grand_banner", "flag", "tower_flag",
            "portable_standard", "iron_helmet", "iron_chestplate", "iron_leggings",
            "iron_boots", "horse_armor");

    public static final RegistryObject<CreativeModeTab> MEDIEVAL_TAB = TABS.register("medieval_coins",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.medieval_coins"))
                    .icon(() -> Coins.GOLD_COIN.get().getDefaultInstance())
                    .displayItems((params, output) -> {})
                    .build());

    public static final RegistryObject<CreativeModeTab> HERALDRY_TAB = TABS.register("houses_and_standards",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.medieval_coins.heraldry"))
                    .icon(() -> BlockInit.HOUSE_DECORATIONS.get("renblood_royal_seal")
                            .get().asItem().getDefaultInstance())
                    .withTabsBefore(MEDIEVAL_TAB.getId())
                    .displayItems((params, output) -> {})
                    .build());

    public static final RegistryObject<CreativeModeTab> MUSIC_TAB=TABS.register("music",
        ()->CreativeModeTab.builder().title(Component.translatable("itemGroup.medieval_coins.music"))
            .icon(()->ItemInit.PIPPIN_MUSIC_DISC.get().getDefaultInstance()).withTabsBefore(HERALDRY_TAB.getId()).displayItems((params,output)->{}).build());

    public static <T extends Item> RegistryObject<T> addToTab(RegistryObject<T> item) {
        MEDIEVAL_TAB_ITEMS.add(item);
        return item;
    }

    public static <T extends Item> RegistryObject<T> addToHeraldry(RegistryObject<T> item) {
        HERALDRY_ITEMS.add(item);
        return item;
    }

    private static String family(RegistryObject<? extends Item> item) {
        String path = item.getId().getPath();
        return path.startsWith("renblood_royal_") ? "" : path.substring(0, path.indexOf('_'));
    }

    private static int heraldryOrder(RegistryObject<? extends Item> item) {
        String path = item.getId().getPath();
        int rank = HERALDRY_ORDER.indexOf(path.substring(path.indexOf('_') + 1));
        return rank < 0 ? HERALDRY_ORDER.size() : rank;
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey().equals(MUSIC_TAB.getKey())) {
            fr.renblood.medievalcoins.music.MusicDiscs.DISCS.values().forEach(item->event.accept(item.get()));
        } else if (event.getTabKey().equals(HERALDRY_TAB.getKey())) {
            HERALDRY_ITEMS.stream()
                    .sorted(Comparator.comparing(CreativeTab::family)
                            .thenComparingInt(CreativeTab::heraldryOrder)
                            .thenComparing(item -> item.getId().getPath()))
                    .forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey().equals(MEDIEVAL_TAB.getKey())) {
            Set<ItemLike> items = new LinkedHashSet<>();
            MEDIEVAL_TAB_ITEMS.forEach(item -> items.add(item.get()));
            // Cover new bonus items even if their registration omitted addToTab.
            Coins.ITEMS.getEntries().forEach(item -> items.add(item.get()));
            ItemInit.REGISTRY.getEntries().forEach(item -> items.add(item.get()));
            HERALDRY_ITEMS.forEach(item -> items.remove(item.get()));
            fr.renblood.medievalcoins.music.MusicDiscs.DISCS.values().forEach(item->items.remove(item.get()));
            items.forEach(event::accept);
        }
    }
}
