package com.yesmenn.technicals.creeper.block;

import com.mojang.serialization.MapCodec;
import com.yesmenn.technicals.creeper.item.CodeSheetItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The CreeperSpeaker3000 block: a half-height speaker (standing or hanging upside down) that becomes a full speaker
 * when a second one is used on it. {@link #BASE} shows a mounting plate when it sits on or hangs from a solid block.
 */
public class CreeperSpeaker3000Block extends BaseEntityBlock {
    public static final MapCodec<CreeperSpeaker3000Block> CODEC = simpleCodec(CreeperSpeaker3000Block::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty UPSIDE_DOWN = BooleanProperty.create("upside_down");
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    public static final BooleanProperty BASE = BooleanProperty.create("base");

    private static final VoxelShape NORTH = CreeperBlocks.boxes(0, 0, 0, 16, 8, 16, 0, 8, 8, 16, 16, 16);
    private static final VoxelShape NORTH_HANGING = CreeperBlocks.boxes(0, 8, 0, 16, 16, 16, 0, 0, 8, 16, 8, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[8];

    static {
        for (int turns = 0; turns < 4; turns++) {
            SHAPES[turns] = CreeperBlocks.turn(NORTH, turns);
            SHAPES[4 + turns] = CreeperBlocks.turn(NORTH_HANGING, turns);
        }
    }

    public CreeperSpeaker3000Block(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UPSIDE_DOWN, false)
                .setValue(FULL, false).setValue(BASE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            CreeperBlocks.openSpeakerScreen.accept(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof CodeSheetItem) {
            return CodeSheetItem.useOn(level, pos, stack, player);
        }
        if (CreeperBlocks.isWrench(stack)) {
            if (!level.isClientSide) {
                BlockState turned = state.setValue(FACING, state.getValue(FACING).getClockWise());
                level.setBlock(pos, turned.setValue(BASE, hasBase(level, pos, turned)), Block.UPDATE_ALL);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // A second speaker makes it a full block.
        if (stack.is(asItem()) && !state.getValue(FULL)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(FULL, true).setValue(BASE, false), Block.UPDATE_ALL);
                level.playSound(null, pos, state.getSoundType().getPlaceSound(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreeperSpeaker3000BlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FULL)) {
            return Shapes.block();
        }
        int turns = state.getValue(FACING).get2DDataValue() + 2 & 3;
        return SHAPES[(state.getValue(UPSIDE_DOWN) ? 4 : 0) + turns];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        boolean hanging = face == Direction.DOWN
                || face != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5;
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(UPSIDE_DOWN, hanging);
        return state.setValue(BASE, hasBase(context.getLevel(), pos, state));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(FULL)) {
            return state.getValue(BASE) ? state.setValue(BASE, false) : state;
        }
        if (direction != top(state)) {
            return state;
        }
        return state.setValue(BASE, isSupport(level, neighborPos, neighborState));
    }

    /** The side the mounting plate points to: up when standing (it hangs under the top), down when hanging. */
    private static Direction top(BlockState state) {
        return state.getValue(UPSIDE_DOWN) ? Direction.DOWN : Direction.UP;
    }

    private static boolean hasBase(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(FULL)) {
            return false;
        }
        BlockPos supportPos = pos.relative(top(state));
        return isSupport(level, supportPos, level.getBlockState(supportPos));
    }

    /** Full blocks, fences and anything with a reasonably large collision box. */
    private static boolean isSupport(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof FenceBlock || state.isCollisionShapeFullBlock(level, pos)) {
            return true;
        }
        for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
            if (box.getYsize() >= 0.25 && (box.getXsize() >= 0.5 || box.getZsize() >= 0.5)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPSIDE_DOWN, FULL, BASE);
    }
}
