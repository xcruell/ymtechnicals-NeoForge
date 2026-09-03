package com.yesmenn.technicals.block.entity;

import com.yesmenn.technicals.block.Soundeffectz3000Block;
import com.yesmenn.technicals.network.OpenSoundeffectz3000ScreenPayload;
import com.yesmenn.technicals.network.Soundeffectz3000PlaybackPayload;
import com.yesmenn.technicals.network.Soundeffectz3000StopPayload;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class Soundeffectz3000BlockEntity extends BlockEntity {
    public static final int DEFAULT_VOLUME = 100;
    public static final int DEFAULT_PITCH = 100;
    public static final int DEFAULT_RANGE = 32;

    private String soundId = "minecraft:block.note_block.pling";
    private int volume = DEFAULT_VOLUME;
    private int pitch = DEFAULT_PITCH;
    private int range = DEFAULT_RANGE;
    private boolean looping;
    private boolean redstoneEnabled = true;
    private boolean redstonePowered;
    private boolean playing;
    private int sequence;
    private int syncTicks;

    public Soundeffectz3000BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SOUNDEFFECTZ_3000.get(), pos, state);
    }

    public OpenSoundeffectz3000ScreenPayload createOpenScreenPayload() {
        return new OpenSoundeffectz3000ScreenPayload(
                worldPosition, soundId, volume, pitch, range, looping, redstoneEnabled, playing);
    }

    public void applySettings(String nextSoundId, int nextVolume, int nextPitch, int nextRange,
                              boolean nextLooping, boolean nextRedstoneEnabled) {
        ResourceLocation id = ResourceLocation.tryParse(nextSoundId);
        if (id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            soundId = id.toString();
        }
        volume = Math.max(0, Math.min(200, nextVolume));
        pitch = Math.max(25, Math.min(400, nextPitch));
        range = Math.max(1, Math.min(128, nextRange));
        looping = nextLooping;
        redstoneEnabled = nextRedstoneEnabled;
        markUpdated();
    }

    public void play() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            return;
        }
        sequence++;
        playing = looping;
        syncTicks = 0;
        sendPlayback(serverLevel);
        markUpdated();
    }

    public void refreshPlayingSound() {
        if (!playing || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!looping) {
            stop();
            return;
        }
        sequence++;
        syncTicks = 0;
        sendPlayback(serverLevel);
        markUpdated();
    }

    public void stop() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        sequence++;
        playing = false;
        for (ServerPlayer player : serverLevel.players()) {
            PacketDistributor.sendToPlayer(player, new Soundeffectz3000StopPayload(worldPosition, sequence));
        }
        markUpdated();
    }

    public void handleNeighborSignal() {
        if (level == null || level.isClientSide || !redstoneEnabled) {
            return;
        }
        boolean poweredNow = level.hasNeighborSignal(worldPosition);
        if (poweredNow == redstonePowered) {
            return;
        }
        redstonePowered = poweredNow;
        BlockState state = getBlockState();
        if (state.hasProperty(Soundeffectz3000Block.POWERED)) {
            level.setBlock(worldPosition, state.setValue(Soundeffectz3000Block.POWERED, poweredNow), Block.UPDATE_CLIENTS);
        }
        if (poweredNow) {
            play();
        } else if (looping && playing) {
            stop();
        }
        markUpdated();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, Soundeffectz3000BlockEntity blockEntity) {
        if (level.isClientSide || !blockEntity.playing || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++blockEntity.syncTicks >= 20) {
            blockEntity.syncTicks = 0;
            blockEntity.sendPlayback(serverLevel);
        }
    }

    private void sendPlayback(ServerLevel level) {
        Soundeffectz3000PlaybackPayload payload = new Soundeffectz3000PlaybackPayload(
                worldPosition, soundId, volume, pitch, range, looping, sequence);
        double maxDistanceSqr = (range + 8.0D) * (range + 8.0D);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) <= maxDistanceSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        soundId = tag.contains("SoundId") ? tag.getString("SoundId") : soundId;
        volume = tag.contains("Volume") ? tag.getInt("Volume") : DEFAULT_VOLUME;
        pitch = tag.contains("Pitch") ? tag.getInt("Pitch") : DEFAULT_PITCH;
        range = tag.contains("Range") ? tag.getInt("Range") : DEFAULT_RANGE;
        looping = tag.getBoolean("Looping");
        redstoneEnabled = !tag.contains("RedstoneEnabled") || tag.getBoolean("RedstoneEnabled");
        redstonePowered = tag.getBoolean("RedstonePowered");
        playing = tag.getBoolean("Playing") && looping;
        sequence = tag.getInt("Sequence");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("SoundId", soundId);
        tag.putInt("Volume", volume);
        tag.putInt("Pitch", pitch);
        tag.putInt("Range", range);
        tag.putBoolean("Looping", looping);
        tag.putBoolean("RedstoneEnabled", redstoneEnabled);
        tag.putBoolean("RedstonePowered", redstonePowered);
        tag.putBoolean("Playing", playing);
        tag.putInt("Sequence", sequence);
    }

    private void markUpdated() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
