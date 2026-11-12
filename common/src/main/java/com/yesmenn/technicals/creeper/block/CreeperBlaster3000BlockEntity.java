package com.yesmenn.technicals.creeper.block;

import com.yesmenn.technicals.creeper.CreeperCode;
import com.yesmenn.technicals.creeper.network.ChannelState;
import com.yesmenn.technicals.creeper.server.CreeperChannels;
import com.yesmenn.technicals.creeper.server.CreeperServer;
import com.yesmenn.technicals.creeper.server.SongLibrary;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The CreeperBlaster3000: holds the song choice and the play state of its channel (its pairing code). The play position
 * is not counted per tick: it is {@code gameTime - startTick} while playing, so all clients can compute it themselves.
 * Every change is pushed as a data copy to {@link CreeperChannels}, which tells the clients.
 */
public class CreeperBlaster3000BlockEntity extends BlockEntity {
    public static final int DEFAULT_RANGE = 32;
    public static final int MAX_RANGE = 128;
    public static final int DEFAULT_VOLUME = 100;

    /** -1 until the first load picks a free code. */
    private int code = -1;
    private int range = DEFAULT_RANGE;
    private int volume = DEFAULT_VOLUME;
    private boolean loop;
    private String songId = "";
    private String title = "";
    private long songTicks;
    private ChannelState.Mode mode = ChannelState.Mode.STOPPED;
    private long startTick;
    private long pausedTicks;
    private int version;
    private boolean redstone;
    private String link = "";
    /** Song file name of a blaster from the old YesMenn mod, resolved through the library on load. */
    private String legacySong = "";

    public CreeperBlaster3000BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CREEPER_BLASTER_3000.get(), pos, state);
    }

    // --- getters (client screen, renderer) ---

    public int code() {
        return Math.max(0, code);
    }

    public int range() {
        return range;
    }

    public int volume() {
        return volume;
    }

    public boolean loop() {
        return loop;
    }

    public String songId() {
        return songId;
    }

    public String title() {
        return title;
    }

    public long songTicks() {
        return songTicks;
    }

    public ChannelState.Mode mode() {
        return mode;
    }

    public boolean isPlaying() {
        return mode == ChannelState.Mode.PLAYING;
    }

    public String link() {
        return link;
    }

    public long positionTicks(long gameTime) {
        long position = mode == ChannelState.Mode.PLAYING ? gameTime - startTick : pausedTicks;
        return songTicks > 0 ? Math.max(0, Math.min(songTicks, position)) : Math.max(0, position);
    }

    // --- lifecycle ---

    /**
     * Joined the level (chunk loaded or placed). NeoForge calls it directly (block entity extension), Fabric through its
     * block entity load event.
     */
    public void onLoad() {
        if (level instanceof ServerLevel serverLevel) {
            if (code < 0) {
                code = CreeperChannels.freeCode(serverLevel, CreeperCode.DEFAULT, worldPosition, serverLevel.random);
                setChanged();
            }
            // A re-download while this blaster was unloaded: follow the song to its new file.
            SongLibrary songs = SongLibrary.get();
            if (songs != null && !songId.isEmpty() && songs.get(songId) == null) {
                SongLibrary.Entry newer = songs.resolve(songId);
                if (newer != null) {
                    useSong(newer);
                    setChanged();
                }
            }
            if (!legacySong.isEmpty()) {
                SongLibrary library = SongLibrary.get();
                SongLibrary.Entry entry = library == null ? null : library.byLegacyName(legacySong);
                if (entry != null && songId.isEmpty()) {
                    useSong(entry);
                }
                legacySong = "";
                setChanged();
            }
            publish(serverLevel);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            CreeperChannels.removeBlaster(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreeperBlaster3000BlockEntity blaster) {
        if (blaster.mode != ChannelState.Mode.PLAYING || blaster.songTicks <= 0) {
            return;
        }
        long position = level.getGameTime() - blaster.startTick;
        if (position < blaster.songTicks) {
            return;
        }
        if (blaster.loop) {
            // Also right after a long unload: jump to where the loop would be now.
            blaster.startTick += blaster.songTicks * (position / blaster.songTicks);
            blaster.changed(true);
        } else {
            blaster.stop();
        }
    }

    // --- actions (server) ---

    public void play(@Nullable ServerPlayer player) {
        if (songId.isEmpty()) {
            if (player != null) {
                CreeperServer.status(player, worldPosition,
                        Component.translatable("gui.ymtechnicals.creeper_blaster.no_song"), -1);
            }
            return;
        }
        if (mode == ChannelState.Mode.PLAYING || level == null) {
            return;
        }
        long from = songTicks > 0 && pausedTicks >= songTicks ? 0 : pausedTicks;
        startTick = level.getGameTime() - from;
        mode = ChannelState.Mode.PLAYING;
        changed(true);
    }

    public void pause() {
        if (mode != ChannelState.Mode.PLAYING || level == null) {
            return;
        }
        pausedTicks = positionTicks(level.getGameTime());
        mode = ChannelState.Mode.PAUSED;
        changed(true);
    }

    public void stop() {
        if (mode == ChannelState.Mode.STOPPED && pausedTicks == 0) {
            return;
        }
        mode = ChannelState.Mode.STOPPED;
        pausedTicks = 0;
        changed(true);
    }

    public void seek(int seconds) {
        if (level == null || songId.isEmpty()) {
            return;
        }
        long target = Math.max(0, (long) seconds * 20);
        if (songTicks > 0) {
            target = Math.min(target, Math.max(0, songTicks - 1));
        }
        if (mode == ChannelState.Mode.PLAYING) {
            startTick = level.getGameTime() - target;
        } else {
            pausedTicks = target;
            if (mode == ChannelState.Mode.STOPPED) {
                mode = ChannelState.Mode.PAUSED;
            }
        }
        changed(true);
    }

    /** Chooses a song; a playing blaster plays the new one from the start. */
    public void selectSong(SongLibrary.Entry entry) {
        useSong(entry);
        pausedTicks = 0;
        if (mode == ChannelState.Mode.PLAYING && level != null) {
            startTick = level.getGameTime();
        } else {
            mode = ChannelState.Mode.STOPPED;
        }
        changed(true);
    }

    /** The same song in a new file (re-download): keeps playing at the same position. */
    public void replaceSong(SongLibrary.Entry entry) {
        useSong(entry);
        changed(true);
    }

    private void useSong(SongLibrary.Entry entry) {
        songId = entry.id;
        title = entry.title;
        songTicks = entry.ticks > 0 ? entry.ticks : entry.seconds * 20L;
    }

    /** The song was deleted from the library. */
    public void clearSong() {
        songId = "";
        title = "";
        songTicks = 0;
        mode = ChannelState.Mode.STOPPED;
        pausedTicks = 0;
        changed(true);
    }

    public void setLink(String link) {
        String trimmed = link.trim();
        this.link = trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** False when another loaded blaster of the dimension already uses the code. */
    public boolean setCode(int newCode) {
        int value = CreeperCode.clamp(newCode);
        if (value == code) {
            return true;
        }
        if (level instanceof ServerLevel serverLevel && CreeperChannels.isTaken(serverLevel, value, worldPosition)) {
            return false;
        }
        code = value;
        changed(false);
        return true;
    }

    public void setRange(int range) {
        this.range = Math.max(1, Math.min(MAX_RANGE, range));
        changed(false);
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        changed(false);
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
        changed(false);
    }

    /** Rising redstone edge toggles play / pause. */
    public void neighborChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered == redstone) {
            return;
        }
        redstone = powered;
        setChanged();
        if (powered) {
            if (mode == ChannelState.Mode.PLAYING) {
                pause();
            } else {
                play(null);
            }
        }
    }

    /**
     * @param restart the playback starts anew on the clients (song, position or play state changed)
     */
    private void changed(boolean restart) {
        if (restart) {
            version++;
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            publish(serverLevel);
            BlockState state = getBlockState();
            boolean on = mode == ChannelState.Mode.PLAYING;
            if (state.hasProperty(CreeperBlaster3000Block.POWERED) && state.getValue(CreeperBlaster3000Block.POWERED) != on) {
                serverLevel.setBlock(worldPosition, state.setValue(CreeperBlaster3000Block.POWERED, on),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    private void publish(ServerLevel serverLevel) {
        CreeperChannels.putBlaster(serverLevel, new CreeperChannels.Blaster(worldPosition.immutable(), code(), range,
                volume, songId, title, songTicks, mode, startTick, pausedTicks, loop, version));
    }

    // --- saving / sync ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Code")) {
            code = CreeperCode.clamp(tag.getInt("Code"));
        } else if (tag.contains("Code0")) {
            // CreeperBlaster3000 of the old YesMenn mod
            code = CreeperCode.of(tag.getInt("Code0"), tag.getInt("Code1"), tag.getInt("Code2"));
        }
        range = tag.contains("Range") ? Math.max(1, Math.min(MAX_RANGE, tag.getInt("Range"))) : DEFAULT_RANGE;
        volume = tag.contains("Volume") ? Math.max(0, Math.min(100, tag.getInt("Volume"))) : DEFAULT_VOLUME;
        redstone = tag.getBoolean("Redstone") || tag.getBoolean("RedstonePowered");
        if (tag.contains("Song")) {
            loop = tag.getBoolean("Loop");
            songId = tag.getString("Song");
            title = tag.getString("Title");
            songTicks = tag.getLong("SongTicks");
            int modeIndex = tag.getInt("Mode");
            mode = ChannelState.Mode.values()[Math.max(0, Math.min(2, modeIndex))];
            startTick = tag.getLong("StartTick");
            pausedTicks = tag.getLong("PausedTicks");
            version = tag.getInt("Version");
            link = tag.getString("Link");
            legacySong = tag.getString("CachedFileName");
        } else {
            loop = tag.getBoolean("Looping");
            link = tag.getString("YoutubeUrl");
            legacySong = tag.getString("CachedFileName");
            title = tag.getString("CachedTitle");
            pausedTicks = tag.getInt("PlaybackOffsetSeconds") * 20L;
            mode = pausedTicks > 0 ? ChannelState.Mode.PAUSED : ChannelState.Mode.STOPPED;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (code >= 0) {
            tag.putInt("Code", code);
        }
        tag.putInt("Range", range);
        tag.putInt("Volume", volume);
        tag.putBoolean("Loop", loop);
        tag.putString("Song", songId);
        tag.putString("Title", title);
        tag.putLong("SongTicks", songTicks);
        tag.putInt("Mode", mode.ordinal());
        tag.putLong("StartTick", startTick);
        tag.putLong("PausedTicks", pausedTicks);
        tag.putInt("Version", version);
        tag.putBoolean("Redstone", redstone);
        tag.putString("Link", link);
        if (!legacySong.isEmpty()) {
            tag.putString("CachedFileName", legacySong);
        }
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
