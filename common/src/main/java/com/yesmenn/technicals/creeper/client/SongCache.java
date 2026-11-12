package com.yesmenn.technicals.creeper.client;

import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.OggInfo;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import com.yesmenn.technicals.platform.ClientPlatform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Client side songs: files received from the server are checked against their ID (SHA-256) and kept in
 * {@code ymtechnicals/creeper_blaster_3000/cache} (oldest dropped above {@link #MAX_CACHE_BYTES}), so a song is
 * transferred only once. For playback a song is decoded once into mono 16-bit PCM shared by all its sources; the two
 * last used songs stay decoded. Client thread, except the marked background parts.
 */
public final class SongCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{64}");
    private static final long MAX_CACHE_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_SONG_BYTES = 128 * 1024 * 1024;
    private static final long REQUEST_RETRY_MILLIS = 30_000;
    private static final int DECODED_KEPT = 2;
    /** A decoded song nobody asked for this long is dropped (48 kHz mono is about 5.8 MB per minute). */
    private static final long DECODED_IDLE_MILLIS = 30_000;

    private static final Set<String> AVAILABLE = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static final Map<String, Assembly> ASSEMBLIES = new HashMap<>();
    private static final LinkedHashMap<String, CompletableFuture<Decoded>> DECODED =
            new LinkedHashMap<>(4, 0.75F, true);
    private static final Map<String, Long> DECODED_USED = new HashMap<>();
    private static volatile boolean scanned;

    /** Decoded song: 16-bit signed little-endian mono PCM. */
    public record Decoded(ByteBuffer pcm, int sampleRate) {
        public int bytesPerSecond() {
            return sampleRate * 2;
        }
    }

    private static final class Assembly {
        final byte[] data;
        int received;

        Assembly(int total) {
            data = new byte[total];
        }
    }

    private SongCache() {
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("ymtechnicals").resolve("creeper_blaster_3000")
                .resolve("cache");
    }

    /** Looks at the cache folder once (background) and trims it. */
    public static void scan() {
        if (scanned) {
            return;
        }
        scanned = true;
        Path folder = folder();
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(folder);
                try (var stream = Files.list(folder)) {
                    for (Path file : stream.toList()) {
                        String id = id(file);
                        if (id != null) {
                            AVAILABLE.add(id);
                        }
                    }
                }
                trim(folder);
            } catch (IOException exception) {
                LOGGER.warn("Could not read the CreeperBlaster3000 cache {}", folder, exception);
            }
        }, Util.backgroundExecutor());
    }

    public static boolean has(String id) {
        return AVAILABLE.contains(id);
    }

    public static boolean isLoading(String id) {
        return ASSEMBLIES.containsKey(id);
    }

    /** Asks the server for a song that is not cached (again after a while if nothing came). */
    public static void request(String id) {
        if (!ID.matcher(id).matches() || has(id) || ASSEMBLIES.containsKey(id)) {
            return;
        }
        long now = Util.getMillis();
        Long last = REQUESTED.get(id);
        if (last != null && now - last < REQUEST_RETRY_MILLIS) {
            return;
        }
        REQUESTED.put(id, now);
        ClientPlatform.sendToServer(new CreeperPayloads.SongRequest(id));
    }

    static void receive(CreeperPayloads.SongChunk chunk) {
        String id = chunk.songId();
        if (!ID.matcher(id).matches() || chunk.total() <= 0 || chunk.total() > MAX_SONG_BYTES || has(id)) {
            return;
        }
        Assembly assembly = ASSEMBLIES.get(id);
        if (chunk.offset() == 0 && (assembly == null || assembly.data.length != chunk.total())) {
            assembly = new Assembly(chunk.total());
            ASSEMBLIES.put(id, assembly);
        }
        if (assembly == null || chunk.offset() != assembly.received
                || chunk.offset() + chunk.data().length > assembly.data.length) {
            // Out of order (e.g. after a reconnect): start over with the next request.
            ASSEMBLIES.remove(id);
            REQUESTED.remove(id);
            return;
        }
        System.arraycopy(chunk.data(), 0, assembly.data, assembly.received, chunk.data().length);
        assembly.received += chunk.data().length;
        if (assembly.received < assembly.data.length) {
            return;
        }
        ASSEMBLIES.remove(id);
        byte[] data = assembly.data;
        Path folder = folder();
        CompletableFuture.runAsync(() -> store(folder, id, data), Util.backgroundExecutor());
    }

    /** Background: checks the hash and writes the file. */
    private static void store(Path folder, String id, byte[] data) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            if (!hash.equals(id)) {
                LOGGER.warn("CreeperBlaster3000 song {} arrived damaged, it will be requested again", id);
                return;
            }
            Files.createDirectories(folder);
            Path temp = folder.resolve(id + ".part");
            Files.write(temp, data);
            Files.move(temp, folder.resolve(id + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
            AVAILABLE.add(id);
            trim(folder);
        } catch (IOException | NoSuchAlgorithmException exception) {
            LOGGER.warn("Could not store CreeperBlaster3000 song {}", id, exception);
        }
    }

    /** Decoded PCM of a cached song, or null while it is still being decoded (decoding starts on the first call). */
    @Nullable
    public static Decoded decoded(String id) {
        DECODED_USED.put(id, Util.getMillis());
        CompletableFuture<Decoded> future = DECODED.get(id);
        if (future == null) {
            if (!has(id)) {
                return null;
            }
            Path file = folder().resolve(id + ".ogg");
            future = CompletableFuture.supplyAsync(() -> decode(file), Util.backgroundExecutor());
            DECODED.put(id, future);
            Iterator<Map.Entry<String, CompletableFuture<Decoded>>> oldest = DECODED.entrySet().iterator();
            while (DECODED.size() > DECODED_KEPT && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        if (!future.isDone()) {
            return null;
        }
        try {
            return future.join();
        } catch (RuntimeException exception) {
            // Broken file: forget it, it is downloaded again.
            DECODED.remove(id);
            AVAILABLE.remove(id);
            try {
                Files.deleteIfExists(folder().resolve(id + ".ogg"));
            } catch (IOException ignored) {
                // it is replaced anyway
            }
            LOGGER.warn("Could not decode CreeperBlaster3000 song {}", id, exception);
            return null;
        }
    }

    /**
     * Drops decoded songs nobody asked for in the last 30 seconds (called every second). Playing sources keep their
     * own reference, so this never cuts off a sound.
     */
    public static void sweep() {
        long now = Util.getMillis();
        DECODED_USED.entrySet().removeIf(entry -> {
            boolean idle = now - entry.getValue() > DECODED_IDLE_MILLIS;
            if (idle) {
                DECODED.remove(entry.getKey());
            }
            return idle;
        });
    }

    /** Background: Ogg Vorbis -> mono 16-bit PCM (several channels are mixed down). */
    private static Decoded decode(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            OggInfo.Info info = OggInfo.read(file);
            int channels = info.channels();
            long frames = info.samples();
            if (frames <= 0 || frames * 2 > Integer.MAX_VALUE - 1024) {
                throw new IOException("Unusable song length " + frames);
            }
            ByteBuffer pcm = ByteBuffer.allocateDirect((int) frames * 2).order(ByteOrder.LITTLE_ENDIAN);
            try (InputStream input = new BufferedInputStream(Files.newInputStream(file), 65536)) {
                JOrbisAudioStream stream = new JOrbisAudioStream(input);
                FloatConsumer mixer = new FloatConsumer() {
                    private float sum;
                    private int channel;

                    @Override
                    public void accept(float value) {
                        sum += value;
                        if (++channel < channels) {
                            return;
                        }
                        if (pcm.remaining() >= 2) {
                            float mixed = Math.max(-1.0F, Math.min(1.0F, sum / channels));
                            pcm.putShort((short) Math.round(mixed * 32767.0F));
                        }
                        sum = 0;
                        channel = 0;
                    }
                };
                while (stream.readChunk(mixer)) {
                    if (!pcm.hasRemaining()) {
                        break;
                    }
                }
            }
            pcm.flip();
            // Shared by all sources: they only ever read slices of it.
            return new Decoded(pcm, info.sampleRate());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Background: deletes the oldest songs while the cache is too big. */
    private static void trim(Path folder) throws IOException {
        List<Path> files;
        try (var stream = Files.list(folder)) {
            files = stream.filter(path -> id(path) != null).toList();
        }
        long total = 0;
        for (Path file : files) {
            total += Files.size(file);
        }
        if (total <= MAX_CACHE_BYTES) {
            return;
        }
        List<Path> oldestFirst = files.stream().sorted(Comparator.comparingLong(SongCache::modified)).toList();
        for (Path file : oldestFirst) {
            if (total <= MAX_CACHE_BYTES * 3 / 4) {
                break;
            }
            total -= Files.size(file);
            AVAILABLE.remove(id(file));
            Files.deleteIfExists(file);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return 0;
        }
    }

    @Nullable
    private static String id(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".ogg")) {
            return null;
        }
        String id = name.substring(0, name.length() - 4);
        return ID.matcher(id).matches() ? id : null;
    }

    /** Logout: drop transfers and decoded songs (the files stay). */
    public static void reset() {
        REQUESTED.clear();
        ASSEMBLIES.clear();
        DECODED.clear();
        DECODED_USED.clear();
    }
}
