package com.yesmenn.technicals.creeper.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.OggInfo;
import com.yesmenn.technicals.creeper.network.SongInfo;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The songs of the server: Ogg Vorbis files named by their SHA-256 in
 * {@code <server>/ymtechnicals/creeper_blaster_3000/library/}, with title, artist, adder and length in
 * {@code library.json}. All length/hash work happens once when a song is added, never while playing. Used on the
 * server thread; files are written on the worker.
 */
public final class SongLibrary {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{64}");
    @Nullable
    private static SongLibrary current;

    private final Path root;
    private final Path songs;
    private final Path index;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** One song as stored in library.json. */
    public static final class Entry {
        public String id = "";
        public String title = "";
        public String artist = "";
        public String addedBy = "";
        public int seconds;
        public long ticks;
        /** File name in the YesMenn mod's old CreeperBlaster3000 folder, for blasters placed back then. */
        public String legacyName = "";
        /** UUID of the player who added it (empty for old songs): counts towards that player's quota. */
        public String addedById = "";
        /** Size of the Ogg file. */
        public long bytes;
        /** When it was added (epoch milliseconds). */
        public long addedAt;
        /** Link it was downloaded from (empty for uploaded files and songs of unknown origin): for re-downloads. */
        public String source = "";
        /** IDs of earlier versions of this song (re-downloads): blasters that still point at them get this one. */
        public List<String> replaces = new ArrayList<>();

        /** For the screen of one player: what may that player do with it? */
        public SongInfo info(boolean deletable, boolean redownloadable) {
            return new SongInfo(id, title, artist, addedBy, seconds, bytes, deletable, redownloadable);
        }

        public boolean ownedBy(UUID player) {
            return addedById.equals(player.toString());
        }
    }

    /** "Title [abcdefghijk].ogg": the old YesMenn mod kept the YouTube video ID in the file name. */
    private static final Pattern LEGACY_YOUTUBE = Pattern.compile("\\[([A-Za-z0-9_-]{11})]\\.ogg$");

    /** A finished download, for the daily limit (kept for 24 hours, also when the song is deleted again). */
    private record Download(String player, long bytes, long time) {
    }

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    private final List<Download> downloads = new ArrayList<>();

    private SongLibrary(Path root) {
        this.root = root;
        this.songs = root.resolve("library");
        this.index = root.resolve("library.json");
    }

    /** Server starts (before the worlds load): read the index and take over songs of the old YesMenn mod. */
    public static void start(MinecraftServer server) {
        SongLibrary library = new SongLibrary(root(server));
        library.load();
        library.importLegacy(server.getServerDirectory().resolve("YesMenn").resolve("CreeperBlaster3000"));
        current = library;
    }

    public static void stop() {
        current = null;
    }

    @Nullable
    public static SongLibrary get() {
        return current;
    }

    public static Path root(MinecraftServer server) {
        return server.getServerDirectory().resolve("ymtechnicals").resolve("creeper_blaster_3000")
                .toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path file(String id) {
        return songs.resolve(id + ".ogg");
    }

    @Nullable
    public Entry get(String id) {
        return entries.get(id);
    }

    @Nullable
    public Entry byLegacyName(String fileName) {
        for (Entry entry : entries.values()) {
            if (entry.legacyName.equals(fileName)) {
                return entry;
            }
        }
        return null;
    }

    /** All songs for one player's screen. */
    public List<SongInfo> list(UUID player, boolean deleteAny, boolean mayDownload) {
        List<SongInfo> result = new ArrayList<>(entries.size());
        entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.title.toLowerCase(Locale.ROOT)))
                .forEach(entry -> {
                    boolean mine = deleteAny || entry.ownedBy(player);
                    result.add(entry.info(mine, mine && mayDownload && !entry.source.isEmpty()));
                });
        return result;
    }

    /** The song with this ID, or the newer version that replaced it. */
    @Nullable
    public Entry resolve(String id) {
        Entry entry = entries.get(id);
        if (entry != null) {
            return entry;
        }
        for (Entry candidate : entries.values()) {
            if (candidate.replaces.contains(id)) {
                return candidate;
            }
        }
        return null;
    }

    /** A re-download replaces a song (its old file is deleted, its ID leads to the new one). */
    public void replace(Entry old, Entry fresh) {
        if (!old.id.equals(fresh.id)) {
            fresh.replaces.addAll(old.replaces);
            fresh.replaces.add(old.id);
            remove(old.id);
        }
        entries.put(fresh.id, fresh);
        saveLater();
    }

    /** Library space the player's own songs take. */
    public long usedBytes(UUID player) {
        String id = player.toString();
        long used = 0;
        for (Entry entry : entries.values()) {
            if (entry.addedById.equals(id)) {
                used += entry.bytes;
            }
        }
        return used;
    }

    /** What the player downloaded within the last 24 hours. */
    public long dailyBytes(UUID player) {
        long since = System.currentTimeMillis() - DAY_MILLIS;
        downloads.removeIf(download -> download.time < since);
        String id = player.toString();
        long used = 0;
        for (Download download : downloads) {
            if (download.player.equals(id)) {
                used += download.bytes;
            }
        }
        return used;
    }

    public void recordDownload(UUID player, long bytes) {
        downloads.add(new Download(player.toString(), bytes, System.currentTimeMillis()));
    }

    /** Adds a song whose file already lies at {@link #file}. */
    public void add(Entry entry) {
        entries.put(entry.id, entry);
        saveLater();
    }

    public boolean remove(String id) {
        if (entries.remove(id) == null) {
            return false;
        }
        Path file = file(id);
        CreeperWorker.io(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                LOGGER.warn("Could not delete song {}", file, exception);
            }
            return null;
        });
        saveLater();
        return true;
    }

    /** Moves a finished Ogg file into the library (worker thread); returns its ID. */
    public static String moveIn(Path library, Path ogg) throws IOException {
        String id = sha256(ogg);
        Path target = library.resolve("library").resolve(id + ".ogg");
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            Files.delete(ogg);
        } else {
            Files.move(ogg, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return id;
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
    }

    private void load() {
        try {
            Files.createDirectories(songs);
            if (Files.isRegularFile(index)) {
                List<Entry> loaded = GSON.fromJson(Files.readString(index, StandardCharsets.UTF_8),
                        new TypeToken<List<Entry>>() {
                        }.getType());
                if (loaded != null) {
                    for (Entry entry : loaded) {
                        if (entry != null && entry.id != null && ID.matcher(entry.id).matches()
                                && Files.isRegularFile(file(entry.id))) {
                            normalize(entry);
                            if (entry.bytes <= 0) {
                                entry.bytes = Files.size(file(entry.id));
                            }
                            entries.put(entry.id, entry);
                        }
                    }
                }
            }
            // Files without an index entry (e.g. index deleted): add them with what the file itself tells.
            try (var stream = Files.list(songs)) {
                for (Path file : stream.toList()) {
                    String name = file.getFileName().toString();
                    String id = name.endsWith(".ogg") ? name.substring(0, name.length() - 4) : "";
                    if (ID.matcher(id).matches() && !entries.containsKey(id)) {
                        Entry entry = describe(file, id, "Unknown song", "", "");
                        if (entry != null) {
                            entries.put(id, entry);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Could not read the CreeperBlaster3000 library {}", index, exception);
        }
        save();
    }

    /** Songs from the YesMenn mod (YesMenn/CreeperBlaster3000/downloads), moved over once. */
    private void importLegacy(Path legacyRoot) {
        Path downloads = legacyRoot.resolve("downloads");
        if (!Files.isDirectory(downloads)) {
            return;
        }
        Map<String, JsonObject> metadata = new LinkedHashMap<>();
        try {
            Path metadataFile = downloads.resolve("library_metadata.json");
            if (Files.isRegularFile(metadataFile)) {
                JsonElement json = JsonParser.parseString(Files.readString(metadataFile, StandardCharsets.UTF_8));
                if (json.isJsonObject()) {
                    json.getAsJsonObject().entrySet().forEach(e -> {
                        if (e.getValue().isJsonObject()) {
                            metadata.put(e.getKey(), e.getValue().getAsJsonObject());
                        }
                    });
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not read the old CreeperBlaster3000 song names", exception);
        }
        int imported = 0;
        try (var stream = Files.list(downloads)) {
            for (Path file : stream.filter(path -> path.toString().endsWith(".ogg")).toList()) {
                String name = file.getFileName().toString();
                JsonObject meta = metadata.get(name);
                String title = string(meta, "title");
                if (title.isEmpty()) {
                    title = name.replaceFirst("\\s*\\[[^]]+]\\.ogg$", "").replaceFirst("\\.ogg$", "");
                }
                String id = moveIn(root, file);
                Entry existing = entries.get(id);
                if (existing != null) {
                    existing.legacyName = name;
                    normalize(existing);
                } else {
                    Entry entry = describe(file(id), id, title, "", string(meta, "uploader"));
                    if (entry != null) {
                        entry.legacyName = name;
                        normalize(entry);
                        entries.put(id, entry);
                    }
                }
                imported++;
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not take over the old CreeperBlaster3000 songs from {}", downloads, exception);
        }
        if (imported > 0) {
            LOGGER.info("Took over {} songs of the old CreeperBlaster3000 into {}", imported, songs);
            save();
        }
    }

    @Nullable
    static Entry describe(Path file, String id, String title, String artist, String addedBy) {
        try {
            OggInfo.Info info = OggInfo.read(file);
            Entry entry = new Entry();
            entry.id = id;
            entry.title = title;
            entry.artist = artist;
            entry.addedBy = addedBy;
            entry.seconds = info.seconds();
            entry.ticks = info.ticks();
            entry.bytes = Files.size(file);
            entry.addedAt = System.currentTimeMillis();
            return entry;
        } catch (IOException exception) {
            LOGGER.warn("Skipping unreadable song {}", file, exception);
            return null;
        }
    }

    private static String string(@Nullable JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        String value = object.get(key).getAsString().trim();
        return "Unknown".equals(value) ? "" : value;
    }

    private static void normalize(Entry entry) {
        entry.title = entry.title == null || entry.title.isBlank() ? "Unknown song" : entry.title;
        entry.artist = entry.artist == null ? "" : entry.artist;
        entry.addedBy = entry.addedBy == null ? "" : entry.addedBy;
        entry.legacyName = entry.legacyName == null ? "" : entry.legacyName;
        entry.addedById = entry.addedById == null ? "" : entry.addedById;
        entry.replaces = entry.replaces == null ? new ArrayList<>() : entry.replaces;
        entry.source = entry.source == null ? "" : entry.source;
        if (entry.source.isEmpty()) {
            Matcher matcher = LEGACY_YOUTUBE.matcher(entry.legacyName);
            if (matcher.find()) {
                entry.source = "https://www.youtube.com/watch?v=" + matcher.group(1);
            }
        }
    }

    private void saveLater() {
        String json = GSON.toJson(List.copyOf(entries.values()));
        CreeperWorker.io(() -> {
            write(json);
            return null;
        });
    }

    private void save() {
        write(GSON.toJson(List.copyOf(entries.values())));
    }

    private synchronized void write(String json) {
        try {
            Files.createDirectories(index.getParent());
            Path temp = index.resolveSibling("library.json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, index, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.error("Could not save the CreeperBlaster3000 library {}", index, exception);
        }
    }
}
