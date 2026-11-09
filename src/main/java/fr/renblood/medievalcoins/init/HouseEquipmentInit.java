package fr.renblood.medievalcoins.init;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.creative.CreativeTab;
import fr.renblood.medievalcoins.item.HouseArmorItem;
import fr.renblood.medievalcoins.item.HouseFlagItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

public final class HouseEquipmentInit {
    private HouseEquipmentInit() {}
    public static void register() {
        for (String house : new String[]{"feran", "goodbrook", "linster", "rok", "rollingford", "roxton", "wyne", "banefort", "chalk"}) {
            CreativeTab.addToHeraldry(ItemInit.REGISTRY.register(house + "_sigil", () -> new Item(new Item.Properties())));
            CreativeTab.addToHeraldry(ItemInit.REGISTRY.register(house + "_portable_standard", HouseFlagItem::new));
            for (ArmorItem.Type type : ArmorItem.Type.values()) {
                CreativeTab.addToHeraldry(ItemInit.REGISTRY.register(house + "_iron_" + type.getName(),
                        () -> new HouseArmorItem(house, type)));
            }
            CreativeTab.addToHeraldry(ItemInit.REGISTRY.register(house + "_horse_armor", () ->
                    new HorseArmorItem(5, new ResourceLocation(MedievalCoin.MODID,
                            "textures/entity/horse/armor/" + house + ".png"), new Item.Properties().stacksTo(1))));
        }
    }
}
