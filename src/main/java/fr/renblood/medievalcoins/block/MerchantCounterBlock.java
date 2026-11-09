package fr.renblood.medievalcoins.block;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.counter.MerchantCounterMenus;
import fr.renblood.medievalcoins.market.service.MerchantCounterLocalStore;
import fr.renblood.medievalcoins.market.service.MerchantCounterSyncService;
import fr.renblood.medievalcoins.market.service.MarketPermissions;
import fr.renblood.medievalcoins.market.service.PlayerMarketSecurityService;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Containers;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MerchantCounterBlock extends BaseEntityBlock {
    public MerchantCounterBlock() {
        super(Properties.of().sound(SoundType.WOOD).strength(2.0F, 6.0F).noOcclusion());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MerchantCounterBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MerchantCounterBlockEntity counter) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (MarketPermissions.isAdmin(serverPlayer) && serverPlayer.isCreative()) {
                    counter.assignAdmin(serverPlayer.getGameProfile().getName());
                    MerchantCounterSyncService.counterPlaced(counter);
                    TutorialManager.recordCounterPlaced(serverPlayer, pos);
                    return;
                }

                PlayerMarketSecurityService.ValidationResult validation = PlayerMarketSecurityService.validateCounterCreation(serverPlayer);
                if (!validation.allowed()) {
                    serverPlayer.sendSystemMessage(Component.literal(validation.message()));
                    MedievalCoin.LOGGER.warn("Pose comptoir refusee: joueur={} raison={}", serverPlayer.getUUID(), validation.reason());
                    level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    if (!serverPlayer.getAbilities().instabuild) {
                        ItemStack refund = new ItemStack(asItem());
                        if (!serverPlayer.getInventory().add(refund)) net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), refund);
                    }
                    return;
                }
            }
            counter.assignOwner(player.getUUID(), player.getGameProfile().getName());
            MerchantCounterSyncService.counterPlaced(counter);
            if (player instanceof ServerPlayer serverPlayer) TutorialManager.recordCounterPlaced(serverPlayer, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MerchantCounterBlockEntity counter) {
            for (int slot = 0; slot < MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS; slot++) {
                ItemStack stored = counter.getPlayerMarketInventory().getStackInSlot(slot);
                if (!counter.isCommerceStockMirror() && !stored.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stored);
            }
            MerchantCounterLocalStore.remove(counter.getCounterId());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MerchantCounterBlockEntity counter) {
            MerchantCounterMenus.open(serverPlayer, counter, player.isShiftKeyDown());
            fr.renblood.medievalcoins.market.service.MarketCommerceService.refresh(serverPlayer,counter);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
