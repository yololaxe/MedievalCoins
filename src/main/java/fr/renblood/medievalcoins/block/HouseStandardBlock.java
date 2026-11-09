package fr.renblood.medievalcoins.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.*;

/** Static heraldic decoration: a wall hanging or a freestanding flag. */
public class HouseStandardBlock extends HorizontalDirectionalBlock {
    private final boolean wall;

    public HouseStandardBlock(boolean wall) {
        super(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(1.5F).noOcclusion());
        this.wall = wall;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = wall ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction support = wall ? state.getValue(FACING).getOpposite() : Direction.DOWN;
        BlockPos anchor = pos.relative(support);
        return level.getBlockState(anchor).isFaceSturdy(level, anchor, support.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!wall) return box(1, 0, 1, 15, 16, 15);
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(2, 1, 0, 14, 15, 3);
            case EAST -> box(0, 1, 2, 3, 15, 14);
            case WEST -> box(13, 1, 2, 16, 15, 14);
            default -> box(2, 1, 13, 14, 15, 16);
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return wall ? Shapes.empty() : box(2, 0, 2, 14, 2, 14);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}
