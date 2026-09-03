package com.yesmenn.technicals.block;

import com.mojang.serialization.MapCodec;
import com.yesmenn.technicals.block.entity.Soundeffectz3000BlockEntity;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class Soundeffectz3000Block extends BaseEntityBlock {
    public static final MapCodec<Soundeffectz3000Block> CODEC = simpleCodec(Soundeffectz3000Block::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public Soundeffectz3000Block(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof Soundeffectz3000BlockEntity soundBlock) {
            PacketDistributor.sendToPlayer(serverPlayer, soundBlock.createOpenScreenPayload());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Soundeffectz3000BlockEntity soundBlock) {
            soundBlock.handleNeighborSignal();
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState nextState, boolean movedByPiston) {
        if (!state.is(nextState.getBlock())
                && level.getBlockEntity(pos) instanceof Soundeffectz3000BlockEntity soundBlock) {
            soundBlock.stop();
        }
        super.onRemove(state, level, pos, nextState, movedByPiston);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Soundeffectz3000BlockEntity soundBlock) {
            soundBlock.handleNeighborSignal();
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Soundeffectz3000BlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntityTypes.SOUNDEFFECTZ_3000.get(), Soundeffectz3000BlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
