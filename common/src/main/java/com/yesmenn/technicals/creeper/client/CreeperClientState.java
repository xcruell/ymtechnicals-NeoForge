package com.yesmenn.technicals.creeper.client;

import com.yesmenn.technicals.creeper.network.ChannelState;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import com.yesmenn.technicals.creeper.network.SongInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the client knows about the Creeper system: the channels of its dimension, the library and the status line. */
public final class CreeperClientState {
    private static final Map<Integer, ChannelState> CHANNELS = new HashMap<>();
    private static final CreeperPayloads.Library EMPTY = new CreeperPayloads.Library(List.of(), false, 0, -1, 0, -1,
            -1);
    private static CreeperPayloads.Library library = EMPTY;
    private static int libraryVersion;
    @Nullable
    private static Status status;

    /** Status line of the blaster screen at {@code pos}. */
    public record Status(BlockPos pos, Component text, int progress, boolean busy) {
    }

    private CreeperClientState() {
    }

    @Nullable
    public static ChannelState channel(int code) {
        return CHANNELS.get(code);
    }

    public static Map<Integer, ChannelState> channels() {
        return CHANNELS;
    }

    static void putChannel(ChannelState state) {
        if (state.code() < 0) {
            // Joined, respawned or changed dimension: the server sends all channels again.
            CHANNELS.clear();
        } else if (state.active()) {
            CHANNELS.put(state.code(), state);
        } else {
            CHANNELS.remove(state.code());
        }
    }

    public static boolean isPlaying(int code) {
        ChannelState state = CHANNELS.get(code);
        return state != null && state.playing();
    }

    public static List<SongInfo> library() {
        return library.songs();
    }

    /** Rights and limits of this player (from the last library update). */
    public static CreeperPayloads.Library libraryInfo() {
        return library;
    }

    /** Changes with every library update, so screens know when to rebuild their list. */
    public static int libraryVersion() {
        return libraryVersion;
    }

    static void setLibrary(CreeperPayloads.Library payload) {
        library = payload;
        libraryVersion++;
    }

    @Nullable
    public static Status status(BlockPos pos) {
        return status != null && status.pos().equals(pos) ? status : null;
    }

    public static void setStatus(Status newStatus) {
        status = newStatus;
    }

    public static void reset() {
        CHANNELS.clear();
        library = EMPTY;
        status = null;
    }
}
