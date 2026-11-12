package com.yesmenn.technicals.creeper.block;

import com.mojang.serialization.MapCodec;
import com.yesmenn.technicals.creeper.item.CodeSheetItem;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The CreeperBlaster3000 block: opens its screen, takes redstone (play/pause) and the code sheet. */
public class CreeperBlaster3000Block extends BaseEntityBlock {
    public static final MapCodec<CreeperBlaster3000Block> CODEC = simpleCodec(CreeperBlaster3000Block::new);
    /** Lit front while playing. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH = CreeperBlocks.boxes(
            0, 0, 0, 16, 1, 16,
            1, 1, 7, 15, 15, 15,
            1, 1, 1, 15, 9.5, 7,
            1, 9, 3, 15, 10.5, 7,
            1, 10, 4, 15, 11.5, 7,
            1, 11, 5, 15, 12.5, 7,
            1, 12, 6, 15, 13.5, 7,
            0, 1, 0, 1, 8, 1,
            15, 1, 0, 16, 8, 1,
            0, 1, 15, 1, 15, 16,
            15, 1, 15, 16, 15, 16,
            0, 8, 0, 16, 9, 1,
            0, 15, 7, 16, 16, 8,
            0, 15, 15, 16, 16, 16,
            0, 15, 7, 1, 16, 16,
            15, 15, 7, 16, 16, 16);
    private static final VoxelShape[] SHAPES = {NORTH, CreeperBlocks.turn(NORTH, 1), CreeperBlocks.turn(NORTH, 2),
            CreeperBlocks.turn(NORTH, 3)};

    public CreeperBlaster3000Block(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            CreeperBlocks.openBlasterScreen.accept(pos);
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
                level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), Block.UPDATE_ALL);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean isMoving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CreeperBlaster3000BlockEntity blaster) {
            blaster.neighborChanged();
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreeperBlaster3000BlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntityTypes.CREEPER_BLASTER_3000.get(),
                CreeperBlaster3000BlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue() + 2 & 3];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING);
    }
}
