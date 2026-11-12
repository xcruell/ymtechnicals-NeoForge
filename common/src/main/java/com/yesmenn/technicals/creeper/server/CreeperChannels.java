package com.yesmenn.technicals.creeper.server;

import com.yesmenn.technicals.creeper.CreeperCode;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.network.ChannelState;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import com.yesmenn.technicals.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The loaded blasters and speakers of every dimension, as plain data copies the block entities push here when they
 * load, change or unload. Nothing in here ever reads a block entity or a chunk, so it can neither load chunks nor touch
 * removed block entities. Changes are collected and sent once at the end of the level tick, one packet per changed
 * code. Server thread only.
 */
public final class CreeperChannels {
    /** Code of the "forget all channels" packet sent before a full sync. */
    public static final int RESET = -1;
    private static final Map<ResourceKey<Level>, Dimension> DIMENSIONS = new HashMap<>();

    /** The blaster part of a channel, as pushed by the blaster. */
    public record Blaster(BlockPos pos, int code, int range, int volume, String songId, String title, long songTicks,
                          ChannelState.Mode mode, long startTick, long pausedTicks, boolean loop, int version) {
    }

    private record Speaker(int code, int range, int volume) {
    }

    private static final class Dimension {
        final Map<BlockPos, Blaster> blasters = new LinkedHashMap<>();
        final Map<BlockPos, Speaker> speakers = new HashMap<>();
        final Set<Integer> dirty = new HashSet<>();

        /** The blaster that owns a code: the first loaded one; others with the same code stay silent. */
        @Nullable
        Blaster owner(int code) {
            for (Blaster blaster : blasters.values()) {
                if (blaster.code() == code) {
                    return blaster;
                }
            }
            return null;
        }
    }

    private CreeperChannels() {
    }

    private static Dimension dimension(ServerLevel level) {
        return DIMENSIONS.computeIfAbsent(level.dimension(), key -> new Dimension());
    }

    public static void putBlaster(ServerLevel level, Blaster blaster) {
        Dimension dimension = dimension(level);
        Blaster old = dimension.blasters.put(blaster.pos(), blaster);
        if (old != null && old.code() != blaster.code()) {
            dimension.dirty.add(old.code());
        }
        dimension.dirty.add(blaster.code());
    }

    public static void removeBlaster(ServerLevel level, BlockPos pos) {
        Dimension dimension = dimension(level);
        Blaster old = dimension.blasters.remove(pos);
        if (old != null) {
            dimension.dirty.add(old.code());
        }
    }

    public static void putSpeaker(ServerLevel level, BlockPos pos, int code, int range, int volume) {
        Dimension dimension = dimension(level);
        Speaker old = dimension.speakers.put(pos.immutable(), new Speaker(code, range, volume));
        if (old != null && old.code() != code) {
            dimension.dirty.add(old.code());
        }
        dimension.dirty.add(code);
    }

    public static void removeSpeaker(ServerLevel level, BlockPos pos) {
        Dimension dimension = dimension(level);
        Speaker old = dimension.speakers.remove(pos);
        if (old != null) {
            dimension.dirty.add(old.code());
        }
    }

    /** True when another loaded blaster of this dimension already uses the code. */
    public static boolean isTaken(ServerLevel level, int code, BlockPos except) {
        for (Blaster blaster : dimension(level).blasters.values()) {
            if (blaster.code() == code && !blaster.pos().equals(except)) {
                return true;
            }
        }
        return false;
    }

    /** The preferred code if no loaded blaster uses it, else a random free one. */
    public static int freeCode(ServerLevel level, int preferred, BlockPos except, RandomSource random) {
        if (!isTaken(level, preferred, except)) {
            return preferred;
        }
        for (int attempt = 0; attempt < 64; attempt++) {
            int code = random.nextInt(CreeperCode.COUNT);
            if (!isTaken(level, code, except)) {
                return code;
            }
        }
        return preferred;
    }

    /** True when this blaster is the one that plays its code (no other loaded blaster with the code came first). */
    public static boolean isOwner(ServerLevel level, BlockPos pos, int code) {
        Blaster owner = dimension(level).owner(code);
        return owner == null || owner.pos().equals(pos);
    }

    /** Sends the changed channels of a level (end of its tick). */
    public static void flush(ServerLevel level) {
        Dimension dimension = DIMENSIONS.get(level.dimension());
        if (dimension == null || dimension.dirty.isEmpty() || level.players().isEmpty()) {
            if (dimension != null && level.players().isEmpty()) {
                // Nobody to tell; players get everything when they join the dimension.
                dimension.dirty.clear();
            }
            return;
        }
        for (int code : dimension.dirty) {
            Platform.sendToLevel(level, new CreeperPayloads.Channel(state(dimension, code)));
        }
        dimension.dirty.clear();
    }

    /** Everything a player needs after joining a dimension. */
    public static void sync(ServerPlayer player) {
        // Code -1 first: the client forgets the channels of its old dimension.
        Platform.sendToPlayer(player, new CreeperPayloads.Channel(ChannelState.inactive(RESET)));
        Dimension dimension = DIMENSIONS.get(player.level().dimension());
        if (dimension == null) {
            return;
        }
        Set<Integer> codes = new HashSet<>();
        for (Blaster blaster : dimension.blasters.values()) {
            if (codes.add(blaster.code())) {
                Platform.sendToPlayer(player, new CreeperPayloads.Channel(state(dimension, blaster.code())));
            }
        }
    }

    private static ChannelState state(Dimension dimension, int code) {
        Blaster blaster = dimension.owner(code);
        if (blaster == null) {
            return ChannelState.inactive(code);
        }
        List<ChannelState.Source> speakers = new ArrayList<>();
        dimension.speakers.forEach((pos, speaker) -> {
            if (speaker.code() == code) {
                speakers.add(new ChannelState.Source(pos, speaker.range(), speaker.volume()));
            }
        });
        return new ChannelState(code, true, blaster.pos(), blaster.range(), blaster.volume(), blaster.songId(),
                blaster.title(), blaster.songTicks(), blaster.mode(), blaster.startTick(), blaster.pausedTicks(),
                blaster.loop(), blaster.version(), List.copyOf(speakers));
    }

    /** A re-download replaced a song: loaded blasters with the old one switch to the new file at the same place. */
    public static void songReplaced(MinecraftServer server, String oldId, SongLibrary.Entry fresh) {
        DIMENSIONS.forEach((key, dimension) -> {
            ServerLevel level = server.getLevel(key);
            if (level == null) {
                return;
            }
            for (Blaster blaster : List.copyOf(dimension.blasters.values())) {
                // The registry only holds loaded blasters, so this never loads a chunk.
                if (blaster.songId().equals(oldId)
                        && level.getBlockEntity(blaster.pos()) instanceof CreeperBlaster3000BlockEntity entity) {
                    entity.replaceSong(fresh);
                }
            }
        });
    }

    /** Server stopped. */
    public static void clear() {
        DIMENSIONS.clear();
    }
}
