package fr.renblood.medievalcoins.tree.fertilize;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FertilizerItem extends Item {
    public FertilizerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockState state = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (!(state.getBlock() instanceof SaplingBlock)) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(
                        Component.literal("⚠️ Fertilizer ne marche que sur des jeunes pousses d’arbres !"), true
                );
            }
            return InteractionResult.FAIL;
        }
        // déléguer à la logique vanilla bone meal
        return Items.BONE_MEAL.useOn(ctx);
    }

    @Override
    public boolean canBeDepleted() {
        return false; // pas de durabilité
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // effet brillant
    }
}
