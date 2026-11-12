package com.yesmenn.technicals.creeper.server;

import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.CreeperConfig;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.yesmenn.technicals.platform.Platform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sends song files to the clients that ask for them (they keep them in their cache, so each player gets a song only
 * once). The file is read on the worker; on the server thread at most {@code transferKiBPerSecond} per player go out
 * each tick, in pieces of {@link CreeperPayloads#CHUNK_SIZE}. Server thread only.
 */
public final class SongTransfers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_QUEUED = 8;
    private static final Map<UUID, Deque<Transfer>> QUEUES = new HashMap<>();
    /** Recently read songs, shared when several players need the same one. */
    private static final Map<String, SoftReference<CompletableFuture<byte[]>>> FILES = new HashMap<>();

    private static final class Transfer {
        final String songId;
        final CompletableFuture<byte[]> data;
        int sent;

        Transfer(String songId, CompletableFuture<byte[]> data) {
            this.songId = songId;
            this.data = data;
        }
    }

    private SongTransfers() {
    }

    public static void request(ServerPlayer player, String songId) {
        SongLibrary library = SongLibrary.get();
        if (library == null || library.get(songId) == null) {
            return;
        }
        Deque<Transfer> queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.size() >= MAX_QUEUED || queue.stream().anyMatch(transfer -> transfer.songId.equals(songId))) {
            return;
        }
        queue.addLast(new Transfer(songId, read(library.file(songId))));
    }

    private static CompletableFuture<byte[]> read(Path file) {
        String key = file.getFileName().toString();
        SoftReference<CompletableFuture<byte[]>> cached = FILES.get(key);
        CompletableFuture<byte[]> data = cached == null ? null : cached.get();
        if (data == null || data.isCompletedExceptionally()) {
            // Forget entries whose data the garbage collector already took.
            FILES.values().removeIf(reference -> reference.get() == null);
            data = CreeperWorker.io(() -> {
                try {
                    return Files.readAllBytes(file);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            FILES.put(key, new SoftReference<>(data));
        }
        return data;
    }

    public static void tick(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        int budget = Math.max(CreeperPayloads.CHUNK_SIZE / 4, CreeperConfig.get().transferKiBPerSecond * 1024 / 20);
        Iterator<Map.Entry<UUID, Deque<Transfer>>> players = QUEUES.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Deque<Transfer>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Deque<Transfer> queue = entry.getValue();
            if (player == null || queue.isEmpty()) {
                players.remove();
                continue;
            }
            int left = budget;
            while (left > 0 && !queue.isEmpty()) {
                Transfer transfer = queue.peekFirst();
                byte[] data = ready(transfer);
                if (data == null) {
                    if (transfer.data.isDone()) {
                        queue.removeFirst();
                        continue;
                    }
                    break;
                }
                int length = Math.min(Math.min(CreeperPayloads.CHUNK_SIZE, left), data.length - transfer.sent);
                Platform.sendToPlayer(player, new CreeperPayloads.SongChunk(transfer.songId, data.length,
                        transfer.sent, Arrays.copyOfRange(data, transfer.sent, transfer.sent + length)));
                transfer.sent += length;
                left -= length;
                if (transfer.sent >= data.length) {
                    queue.removeFirst();
                }
            }
        }
    }

    @Nullable
    private static byte[] ready(Transfer transfer) {
        if (!transfer.data.isDone()) {
            return null;
        }
        try {
            return transfer.data.join();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read song {} for a transfer", transfer.songId, exception);
            return null;
        }
    }

    public static void forget(UUID player) {
        QUEUES.remove(player);
    }

    public static void stop() {
        QUEUES.clear();
        FILES.clear();
    }
}
