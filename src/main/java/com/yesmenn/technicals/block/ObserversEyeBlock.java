package com.yesmenn.technicals.block;

import com.mojang.serialization.MapCodec;
import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class ObserversEyeBlock extends BaseEntityBlock {
    public static final MapCodec<ObserversEyeBlock> CODEC = simpleCodec(ObserversEyeBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
            Block.box(4.0, 3.5, 4.0, 12.0, 5.5, 12.0));
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(4.0, 10.5, 4.0, 12.0, 12.5, 12.0));
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 12.0, 16.0, 16.0, 16.0),
            Block.box(4.0, 4.0, 10.5, 12.0, 12.0, 12.5));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.0),
            Block.box(4.0, 4.0, 3.5, 12.0, 12.0, 5.5));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(12.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(10.5, 4.0, 4.0, 12.5, 12.0, 12.0));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 4.0, 16.0, 16.0),
            Block.box(3.5, 4.0, 4.0, 5.5, 12.0, 12.0));

    public ObserversEyeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(POWERED, false)
                .setValue(FACING, context.getClickedFace());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ObserversEyeBlockEntity sensor) {
            PacketDistributor.sendToPlayer(serverPlayer, sensor.createOpenScreenPayload());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState nextState, boolean movedByPiston) {
        if (!state.is(nextState.getBlock()) && state.getValue(POWERED)) {
            level.updateNeighborsAt(pos, this);
        }
        super.onRemove(state, level, pos, nextState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ObserversEyeBlockEntity sensor) {
            sensor.markUpdated();
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObserversEyeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntityTypes.OBSERVERS_EYE.get(), ObserversEyeBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING);
    }
}
