package fr.renblood.medievalcoins.item;

import fr.renblood.medievalcoins.MedievalCoin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

public class HouseArmorItem extends ArmorItem {
    private final String house;
    public HouseArmorItem(String house, ArmorItem.Type type) {
        super(ArmorMaterials.IRON, type, new Item.Properties());
        this.house = house;
    }
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return MedievalCoin.MODID + ":textures/models/armor/" + house
                + (slot == EquipmentSlot.LEGS ? "_layer_2.png" : "_layer_1.png");
    }
}
