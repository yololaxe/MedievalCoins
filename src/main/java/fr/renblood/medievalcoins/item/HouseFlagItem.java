package fr.renblood.medievalcoins.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Ceremonial standard with an ordinary held-item model in either hand. */
public class HouseFlagItem extends Item {
    public HouseFlagItem() { super(new Item.Properties().stacksTo(1)); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().isEmpty()) {
            if (!level.isClientSide) {
                player.setItemSlot(EquipmentSlot.OFFHAND, stack.copy());
                stack.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.medieval_coins.portable_standard").withStyle(ChatFormatting.GRAY));
    }
}
