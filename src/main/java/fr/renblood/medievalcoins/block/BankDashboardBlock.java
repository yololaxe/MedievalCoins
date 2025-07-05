package fr.renblood.medievalcoins.block;

import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class BankDashboardBlock extends Block {
    public BankDashboardBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GRAVEL)
                .strength(2.0F, 6.0F)
                .noOcclusion()
                .isRedstoneConductor((bs, br, bp) -> false));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(
                    serverPlayer,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.translatable("screen.medievalcoins.banker");
                        }

                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player playerEntity) {
                            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                            buf.writeBlockPos(pos);
                            return new BankerGuiMenu(id, inv, buf);
                        }
                    },
                    pos
            );
        }

        return InteractionResult.SUCCESS;
    }
}
