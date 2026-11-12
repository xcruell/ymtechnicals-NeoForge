package com.yesmenn.technicals.creeper.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What one pairing code (channel) plays in a dimension: the blaster with its song and play position, and every loaded
 * speaker with the same code. Sent to all players of the dimension whenever something changes; the clients play the
 * sound from the sources near them on their own, synchronized by game time.
 *
 * @param version changes whenever the playback restarts (song, position, play/pause), not for speaker changes
 */
public record ChannelState(int code, boolean active, BlockPos blaster, int range, int volume, String songId,
                           String title, long songTicks, Mode mode, long startTick, long pausedTicks, boolean loop,
                           int version, List<Source> speakers) {
    public enum Mode {
        STOPPED, PLAYING, PAUSED
    }

    public record Source(BlockPos pos, int range, int volume) {
    }

    public static ChannelState inactive(int code) {
        return new ChannelState(code, false, BlockPos.ZERO, 0, 0, "", "", 0, Mode.STOPPED, 0, 0, false, 0, List.of());
    }

    /** Play position in ticks at the given game time. */
    public long positionTicks(long gameTime) {
        long position = mode == Mode.PLAYING ? gameTime - startTick : pausedTicks;
        return songTicks > 0 ? Math.max(0, Math.min(songTicks, position)) : Math.max(0, position);
    }

    public boolean playing() {
        return active && mode == Mode.PLAYING && !songId.isEmpty();
    }

    /** The blaster itself and all speakers, as sound sources. */
    public List<Source> sources() {
        List<Source> sources = new ArrayList<>(speakers.size() + 1);
        sources.add(new Source(blaster, range, volume));
        sources.addAll(speakers);
        return sources;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(code);
        buf.writeBoolean(active);
        if (!active) {
            return;
        }
        buf.writeBlockPos(blaster);
        buf.writeVarInt(range);
        buf.writeVarInt(volume);
        buf.writeUtf(songId, 64);
        buf.writeUtf(title, 256);
        buf.writeVarLong(songTicks);
        buf.writeEnum(mode);
        buf.writeLong(startTick);
        buf.writeVarLong(pausedTicks);
        buf.writeBoolean(loop);
        buf.writeVarInt(version);
        buf.writeVarInt(speakers.size());
        for (Source speaker : speakers) {
            buf.writeBlockPos(speaker.pos());
            buf.writeVarInt(speaker.range());
            buf.writeVarInt(speaker.volume());
        }
    }

    public static ChannelState read(FriendlyByteBuf buf) {
        int code = buf.readVarInt();
        if (!buf.readBoolean()) {
            return inactive(code);
        }
        BlockPos blaster = buf.readBlockPos();
        int range = buf.readVarInt();
        int volume = buf.readVarInt();
        String songId = buf.readUtf(64);
        String title = buf.readUtf(256);
        long songTicks = buf.readVarLong();
        Mode mode = buf.readEnum(Mode.class);
        long startTick = buf.readLong();
        long pausedTicks = buf.readVarLong();
        boolean loop = buf.readBoolean();
        int version = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), 4096);
        List<Source> speakers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            speakers.add(new Source(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));
        }
        return new ChannelState(code, true, blaster, range, volume, songId, title, songTicks, mode, startTick,
                pausedTicks, loop, version, List.copyOf(speakers));
    }
}
