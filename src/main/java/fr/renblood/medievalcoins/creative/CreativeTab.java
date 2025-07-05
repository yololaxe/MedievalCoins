package fr.renblood.medievalcoins.creative;

import fr.renblood.medievalcoins.MedievalCoin;
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
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTab {
    // On crée notre onglet
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MedievalCoin.MODID);

    // Liste intermédiaire pour y stocker tous les items à afficher
    public static final List<Supplier<? extends ItemLike>> MEDIEVAL_TAB_ITEMS = new ArrayList<>();

    // Déclaration et enregistrement de l'onglet
    public static final RegistryObject<CreativeModeTab> MEDIEVAL_TAB = TABS.register("medieval_coins",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.medieval_coins"))
                    .icon(() -> Coins.GOLD_COIN.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // on n'affiche pas ici, on le fait plutôt dans buildContents
                    })
                    .build()
    );

    /**
     * À appeler pour chaque item qu'on veut voir dans l'onglet.
     * Ex : CreativeTab.addToTab(Coins.BRONZE_COIN);
     */
    public static <T extends Item> RegistryObject<T> addToTab(RegistryObject<T> item) {
        MEDIEVAL_TAB_ITEMS.add(item);
        return item;
    }

    /** Lors de la construction de l’onglet, on y dépose tous nos items. */
    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == MEDIEVAL_TAB.getKey()) {
            MEDIEVAL_TAB_ITEMS.forEach(itemLike -> event.accept(itemLike.get()));
        }
    }
}
