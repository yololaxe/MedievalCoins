package fr.renblood.medievalcoins.block;

import fr.renblood.medievalcoins.inventory.banker.BankerGuiMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class BankDashboardBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Boîtes correspondant aux trois éléments JSON
    private static final VoxelShape BASE        = Block.box(0,   0,  0, 16,  2, 16);
    private static final VoxelShape PILLAR      = Block.box(4,   2,  4, 12, 15, 12);
    private static final VoxelShape TOP_ELEMENT = Block.box(0,  12,  3, 16, 16, 16);
    private static final VoxelShape COLLISION_SHAPE = Shapes.or(BASE, PILLAR, TOP_ELEMENT);

    public BankDashboardBlock() {
        super(Properties.of()
                .sound(SoundType.WOOD) // Changé de GRAVEL à WOOD
                .strength(2.0F, 6.0F)
                .noOcclusion()
                .isRedstoneConductor((bs, br, bp) -> false)
        );
        // Par défaut face au sud
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Oriented towards the player
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        // Union des trois boîtes pour la collision
        return COLLISION_SHAPE;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        // Empêche le cube vanilla derrière le modèle
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
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
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
