package fr.renblood.medievalcoins.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** A single inventory item places and removes the complete architectural standard. */
public class MonumentalStandardBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 14);
    private final boolean wall;
    private final int columns;
    private final int rows;
    public MonumentalStandardBlock(boolean wall) {
        this(wall, wall ? 2 : 3, wall ? 4 : 5);
    }
    public MonumentalStandardBlock(boolean wall, int columns, int rows) {
        super(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2F).noOcclusion());
        this.wall = wall;
        this.columns = columns;
        this.rows = rows;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }
    private int width() { return columns; }
    private int height() { return rows; }
    private boolean occupied(int x, int y) { return wall || x == 0 || y >= 3; }
    private BlockPos cell(BlockPos root, Direction facing, int x, int y) {
        return root.relative(facing.getClockWise(), x).above(wall ? -y : y);
    }
    private BlockPos root(BlockState state, BlockPos pos) {
        int part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getClockWise(), -(part % width()))
                .above(wall ? part / width() : -(part / width()));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = wall ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, facing);
        Level level = context.getLevel();
        BlockPos root = context.getClickedPos();
        if (!state.canSurvive(level, root)) return null;
        for (int y = 0; y < height(); y++) for (int x = 0; x < width(); x++) {
            if (!occupied(x, y)) continue;
            BlockPos pos = cell(root, facing, x, y);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return state;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        if (level.isClientSide) return;
        for (int y = 0; y < height(); y++) for (int x = 0; x < width(); x++) {
            if (!occupied(x, y) || (x == 0 && y == 0)) continue;
            level.setBlock(cell(pos, state.getValue(FACING), x, y), state.setValue(PART, y * width() + x), 3);
        }
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos base = root(state, pos);
        Direction support = wall ? state.getValue(FACING).getOpposite() : Direction.DOWN;
        BlockPos anchor = base.relative(support);
        return level.getBlockState(anchor).isFaceSturdy(level, anchor, support.getOpposite());
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? super.updateShape(state, side, neighbor, level, pos, neighborPos) : Blocks.AIR.defaultBlockState();
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != 0)
            level.destroyBlock(root(state, pos), false);
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide) {
            BlockPos base = root(state, pos);
            if (state.getValue(PART) != 0) {
                if (level.getBlockState(base).is(this)) level.destroyBlock(base, true);
            } else {
                for (int y = 0; y < height(); y++) for (int x = 0; x < width(); x++) {
                    if (!occupied(x, y) || (x == 0 && y == 0)) continue;
                    BlockPos other = cell(base, state.getValue(FACING), x, y);
                    BlockState part = level.getBlockState(other);
                    if (part.is(this) && part.getValue(FACING) == state.getValue(FACING)
                            && part.getValue(PART) == y * width() + x) level.removeBlock(other, false);
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!wall && state.getValue(PART) / width() < 3) return box(6, 0, 6, 10, 16, 10);
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 0, 16, 16, wall ? 3 : 9);
            case EAST -> box(0, 0, 0, wall ? 3 : 9, 16, 16);
            case WEST -> box(wall ? 13 : 7, 0, 0, 16, 16, 16);
            default -> box(0, 0, wall ? 13 : 7, 16, 16, 16);
        };
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return !wall && state.getValue(PART) % width() == 0 ? box(7, 0, 7, 9, 16, 9) : Shapes.empty();
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
}
