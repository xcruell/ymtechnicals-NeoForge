package com.yesmenn.technicals.creeper.client;

import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import com.yesmenn.technicals.creeper.server.SongUploads;
import com.yesmenn.technicals.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Sends a local audio file to the server for the library: picked with the system file dialog or dropped onto the
 * blaster screen. The file is read in small pieces each tick and sent in order, at most {@link SongUploads#WINDOW}
 * bytes ahead of what the server confirmed. Client thread.
 */
public final class CreeperUploads {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** File endings offered in the file dialog (the server checks its own list). */
    public static final List<String> EXTENSIONS = List.of("mp3", "wav", "ogg", "oga", "flac", "m4a", "aac", "opus",
            "webm");
    /** At most this much per tick (about 2.4 MB/s). */
    private static final int PER_TICK = 4 * CreeperPayloads.UPLOAD_CHUNK_SIZE;
    @Nullable
    private static Upload current;
    /** Files waiting for the running upload (dropped together or chosen together). */
    private static final Deque<Waiting> WAITING = new ArrayDeque<>();
    private static final int MAX_WAITING = 20;

    private record Waiting(BlockPos pos, Path file) {
    }

    private static final class Upload {
        final BlockPos pos;
        final FileChannel channel;
        final long size;
        long sent;
        long acked = -2;

        Upload(BlockPos pos, FileChannel channel, long size) {
            this.pos = pos;
            this.channel = channel;
            this.size = size;
        }
    }

    private CreeperUploads() {
    }

    public static boolean isRunning() {
        return current != null;
    }

    /** Opens the system file dialog (on its own thread, so the game keeps running) and uploads the chosen file. */
    public static void chooseFile(BlockPos pos, String title) {
        Thread thread = new Thread(() -> {
            String chosen;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(EXTENSIONS.size());
                EXTENSIONS.forEach(extension -> filters.put(stack.UTF8("*." + extension)));
                filters.flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, "Audio", true);
            } catch (Throwable error) {
                LOGGER.warn("Could not open the file dialog", error);
                return;
            }
            if (chosen != null) {
                // Several files come separated by "|".
                List<Path> files = new ArrayList<>();
                for (String part : chosen.split("[|]")) {
                    if (!part.isBlank()) {
                        files.add(Path.of(part));
                    }
                }
                Minecraft.getInstance().execute(() -> files.forEach(file -> start(pos, file)));
            }
        }, "ymtechnicals-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    /** Uploads the file, or queues it behind the running upload. */
    public static void start(BlockPos pos, Path file) {
        if (current != null) {
            if (WAITING.size() >= MAX_WAITING) {
                status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.queue_full",
                        MAX_WAITING), -1, true);
                return;
            }
            WAITING.addLast(new Waiting(pos, file));
            status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.queued", WAITING.size()),
                    -1, true);
            return;
        }
        String name = file.getFileName().toString();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        long max = CreeperClientState.libraryInfo().maxUploadBytes();
        try {
            if (max < 0) {
                status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.uploads_off"), -1,
                        false);
                return;
            }
            if (!EXTENSIONS.contains(extension) || !Files.isRegularFile(file)) {
                status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.not_audio"), -1, false);
                return;
            }
            long size = Files.size(file);
            if (size <= 0 || size > max) {
                status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.upload_too_big",
                        max / (1024 * 1024)), -1, false);
                return;
            }
            current = new Upload(pos, FileChannel.open(file, StandardOpenOption.READ), size);
            ClientPlatform.sendToServer(new CreeperPayloads.UploadStart(pos, name, size));
            status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.uploading", 0), 0, true);
        } catch (IOException exception) {
            LOGGER.warn("Could not read {}", file, exception);
            status(pos, Component.translatable("gui.ymtechnicals.creeper_blaster.download.failed",
                    exception.getMessage()), -1, false);
        }
    }

    /** The server confirmed {@code received} bytes, or stopped the upload (-1). */
    static void ack(long received) {
        Upload upload = current;
        if (upload == null) {
            return;
        }
        if (received < 0) {
            close();
            return;
        }
        upload.acked = received;
    }

    public static void tick() {
        Upload upload = current;
        if (upload == null || upload.acked < 0) {
            // Not accepted yet.
            return;
        }
        try {
            int budget = PER_TICK;
            while (budget > 0 && upload.sent < upload.size && upload.sent - upload.acked < SongUploads.WINDOW) {
                int length = (int) Math.min(CreeperPayloads.UPLOAD_CHUNK_SIZE, upload.size - upload.sent);
                ByteBuffer buffer = ByteBuffer.allocate(length);
                while (buffer.hasRemaining()) {
                    if (upload.channel.read(buffer, upload.sent + buffer.position()) < 0) {
                        throw new IOException("file got shorter while uploading");
                    }
                }
                ClientPlatform.sendToServer(new CreeperPayloads.UploadChunk(upload.sent, buffer.array()));
                upload.sent += length;
                budget -= length;
            }
            if (upload.sent >= upload.size) {
                // Empty piece: done; the server converts it and reports from here on.
                ClientPlatform.sendToServer(new CreeperPayloads.UploadChunk(upload.sent, new byte[0]));
                close();
            }
        } catch (IOException exception) {
            LOGGER.warn("Upload failed", exception);
            close();
        }
    }

    private static void status(BlockPos pos, Component text, int progress, boolean busy) {
        CreeperClientState.setStatus(new CreeperClientState.Status(pos, text, progress, busy));
    }

    /** Ends the running upload and starts the next waiting one. */
    private static void close() {
        Upload upload = current;
        current = null;
        if (upload != null) {
            try {
                upload.channel.close();
            } catch (IOException ignored) {
                // nothing left to do
            }
        }
        Waiting next = WAITING.pollFirst();
        if (next != null) {
            start(next.pos(), next.file());
        }
    }

    public static void reset() {
        WAITING.clear();
        close();
    }
}
