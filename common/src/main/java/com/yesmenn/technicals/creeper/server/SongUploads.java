package com.yesmenn.technicals.creeper.server;

import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.CreeperConfig;
import com.yesmenn.technicals.creeper.CreeperPermission;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import com.yesmenn.technicals.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Local audio files players add from their own computer: the client sends the file in order in small pieces (it stays
 * at most {@link #WINDOW} bytes ahead of the acknowledged amount), the server writes them on the worker into a temp file
 * and then converts it like a download ({@link SongDownloads#upload}). Size limit, quota and permission are checked at
 * the start, the result again after converting. Server thread only.
 */
public final class SongUploads {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The client may send this much more than the server confirmed. */
    public static final int WINDOW = 512 * 1024;
    /** An upload that receives nothing for this long is dropped. */
    private static final long IDLE_MILLIS = 60_000;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final BlockPos pos;
        final String fileName;
        final long size;
        final Path file;
        final FileChannel channel;
        long received;
        long lastPacket;
        int shownPercent = -1;
        CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

        Session(BlockPos pos, String fileName, long size, Path file, FileChannel channel) {
            this.pos = pos;
            this.fileName = fileName;
            this.size = size;
            this.file = file;
            this.channel = channel;
            this.lastPacket = System.currentTimeMillis();
        }
    }

    private SongUploads() {
    }

    /** Largest file this player may upload now, or -1 when uploads are not possible for them. */
    public static long maxBytes(ServerPlayer player) {
        CreeperConfig config = CreeperConfig.get();
        if (!config.uploadsAllowed(player.server) || !config.allowDownloads || !CreeperPermission.DOWNLOAD.has(player)) {
            return -1;
        }
        return config.maxUploadMiB * 1024L * 1024L;
    }

    public static void start(ServerPlayer player, CreeperPayloads.UploadStart start) {
        cancel(player.getUUID());
        CreeperConfig config = CreeperConfig.get();
        SongLibrary library = SongLibrary.get();
        long max = maxBytes(player);
        if (library == null || max < 0) {
            reject(player, start.pos(), Component.translatable(
                    "gui.ymtechnicals.creeper_blaster.download.uploads_off"));
            return;
        }
        if (start.size() <= 0 || start.size() > max) {
            reject(player, start.pos(), Component.translatable(
                    "gui.ymtechnicals.creeper_blaster.download.upload_too_big", config.maxUploadMiB));
            return;
        }
        String name = start.fileName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!config.directAudioExtensions.contains(extension)) {
            reject(player, start.pos(), Component.translatable("gui.ymtechnicals.creeper_blaster.download.not_audio"));
            return;
        }
        if (!SongDownloads.mayAdd(player, start.pos(), config)) {
            Platform.sendToPlayer(player, new CreeperPayloads.UploadAck(-1));
            return;
        }
        try {
            Path folder = library.root().resolve("temp").resolve("upload-" + UUID.randomUUID());
            Files.createDirectories(folder);
            Path file = folder.resolve("source." + extension);
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            SESSIONS.put(player.getUUID(), new Session(start.pos(), name, start.size(), file, channel));
            Platform.sendToPlayer(player, new CreeperPayloads.UploadAck(0));
        } catch (IOException exception) {
            LOGGER.warn("Could not start an upload", exception);
            reject(player, start.pos(), Component.translatable("gui.ymtechnicals.creeper_blaster.download.failed",
                    exception.getMessage()));
        }
    }

    public static void chunk(ServerPlayer player, CreeperPayloads.UploadChunk chunk) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (chunk.offset() != session.received || session.received + chunk.data().length > session.size) {
            cancel(player.getUUID());
            reject(player, session.pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.failed",
                    "upload out of order"));
            return;
        }
        session.lastPacket = System.currentTimeMillis();
        if (chunk.data().length > 0) {
            ByteBuffer data = ByteBuffer.wrap(chunk.data());
            long offset = session.received;
            session.received += chunk.data().length;
            // Written on the worker in order (each write waits for the one before).
            session.writes = session.writes.thenRunAsync(() -> {
                try {
                    while (data.hasRemaining()) {
                        session.channel.write(data, offset + data.position());
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }, command -> CreeperWorker.io(() -> {
                command.run();
                return null;
            }));
            Platform.sendToPlayer(player, new CreeperPayloads.UploadAck(session.received));
            int percent = (int) (session.received * 100 / session.size);
            if (percent != session.shownPercent) {
                session.shownPercent = percent;
                CreeperServer.status(player, session.pos, Component.translatable(
                        "gui.ymtechnicals.creeper_blaster.download.uploading", percent), percent);
            }
            return;
        }
        // Empty piece: the client is done.
        if (session.received != session.size) {
            cancel(player.getUUID());
            reject(player, session.pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.failed",
                    "upload incomplete"));
            return;
        }
        SESSIONS.remove(player.getUUID());
        MinecraftServer server = player.server;
        UUID playerId = player.getUUID();
        session.writes.whenComplete((ignored, error) -> {
            try {
                session.channel.close();
            } catch (IOException closeError) {
                LOGGER.debug("Could not close upload file", closeError);
            }
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                if (error != null || online == null) {
                    AudioTools.deleteTree(session.file.getParent());
                    if (online != null) {
                        reject(online, session.pos, Component.translatable(
                                "gui.ymtechnicals.creeper_blaster.download.failed", String.valueOf(error)));
                    }
                    return;
                }
                if (!SongDownloads.mayAdd(online, session.pos, CreeperConfig.get())) {
                    AudioTools.deleteTree(session.file.getParent());
                    return;
                }
                SongDownloads.upload(online, session.pos, session.file, session.fileName);
            });
        });
    }

    private static void reject(ServerPlayer player, BlockPos pos, Component message) {
        Platform.sendToPlayer(player, new CreeperPayloads.UploadAck(-1));
        CreeperServer.status(player, pos, message, -1);
    }

    /** Drops idle uploads (called once per second). */
    public static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (now - session.lastPacket > IDLE_MILLIS) {
                iterator.remove();
                close(session);
            }
        }
    }

    /** Drops a running upload of the player (new upload, logout). */
    public static void cancel(UUID player) {
        Session session = SESSIONS.remove(player);
        if (session != null) {
            close(session);
        }
    }

    private static void close(Session session) {
        session.writes.whenComplete((ignored, error) -> {
            try {
                session.channel.close();
            } catch (IOException exception) {
                LOGGER.debug("Could not close upload file", exception);
            }
            AudioTools.deleteTree(session.file.getParent());
        });
    }

    public static void stop() {
        SESSIONS.values().forEach(SongUploads::close);
        SESSIONS.clear();
    }
}
