package com.yesmenn.technicals.creeper.block;

import com.yesmenn.technicals.creeper.CreeperCode;
import com.yesmenn.technicals.creeper.server.CreeperChannels;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A CreeperSpeaker3000: plays what the blaster with the same pairing code plays. It only stores its code, range and
 * volume and reports them to {@link CreeperChannels}; it has no ticker and never looks up the blaster.
 */
public class CreeperSpeaker3000BlockEntity extends BlockEntity {
    private int code = CreeperCode.DEFAULT;
    private int range = CreeperBlaster3000BlockEntity.DEFAULT_RANGE;
    private int volume = CreeperBlaster3000BlockEntity.DEFAULT_VOLUME;

    public CreeperSpeaker3000BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CREEPER_SPEAKER_3000.get(), pos, state);
    }

    public int code() {
        return code;
    }

    public int range() {
        return range;
    }

    public int volume() {
        return volume;
    }

    /**
     * Joined the level (chunk loaded or placed). NeoForge calls it directly (block entity extension), Fabric through its
     * block entity load event.
     */
    public void onLoad() {
        if (level instanceof ServerLevel serverLevel) {
            CreeperChannels.putSpeaker(serverLevel, worldPosition, code, range, volume);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            CreeperChannels.removeSpeaker(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    public void apply(int code, int range, int volume) {
        this.code = CreeperCode.clamp(code);
        this.range = Math.max(1, Math.min(CreeperBlaster3000BlockEntity.MAX_RANGE, range));
        this.volume = Math.max(0, Math.min(100, volume));
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            CreeperChannels.putSpeaker(serverLevel, worldPosition, this.code, this.range, this.volume);
        }
    }

    public void setCode(int code) {
        apply(code, range, volume);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Code")) {
            code = CreeperCode.clamp(tag.getInt("Code"));
        } else if (tag.contains("Code0")) {
            // CreeperSpeaker3000 of the old YesMenn mod
            code = CreeperCode.of(tag.getInt("Code0"), tag.getInt("Code1"), tag.getInt("Code2"));
        }
        range = tag.contains("Range") ? Math.max(1, Math.min(CreeperBlaster3000BlockEntity.MAX_RANGE,
                tag.getInt("Range"))) : CreeperBlaster3000BlockEntity.DEFAULT_RANGE;
        volume = tag.contains("Volume") ? Math.max(0, Math.min(100, tag.getInt("Volume")))
                : CreeperBlaster3000BlockEntity.DEFAULT_VOLUME;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Code", code);
        tag.putInt("Range", range);
        tag.putInt("Volume", volume);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
