package com.yesmenn.technicals.creeper.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.CreeperConfig;
import com.yesmenn.technicals.creeper.CreeperPermission;
import com.yesmenn.technicals.creeper.OggInfo;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds songs to the library from a link. Links to the configured yt-dlp hosts (YouTube) go through yt-dlp, with its
 * generic web page extractor switched off; other links are only allowed when they point straight at an audio file,
 * which is downloaded here (every redirect checked) and converted with ffmpeg reading local files only. Everything
 * becomes Ogg Vorbis, mono, 48 kHz, quality 6. Links must lead to public internet addresses. Each player has a storage
 * quota and an optional daily limit. One download per player; progress and errors go to the player's blaster screen.
 */
public final class SongDownloads {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern PERCENT = Pattern.compile("\\[download]\\s+([0-9.]+)%");
    private static final long TIMEOUT_MINUTES = 15;
    private static final long MAX_SOURCE_BYTES = 300L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    /** Ogg Vorbis, mono (needed for positional sound), 48 kHz, VBR quality 6 (about 110-130 kbit/s mono). */
    private static final List<String> AUDIO_FORMAT = List.of("-ac", "1", "-ar", "48000", "-q:a", "6");
    /** ffmpeg demuxers allowed for direct links and uploads ("mov" covers mp4/m4a, "matroska" webm/mka). */
    private static final String AUDIO_CONTAINERS = "mp3,wav,ogg,flac,mov,aac,matroska,w64,aiff";
    /** Players with a running download (server thread). */
    private static final Set<UUID> RUNNING = new HashSet<>();
    /** Waiting jobs per player; the next one starts when the running one is finished. */
    private static final Map<UUID, Deque<Pending>> QUEUES = new HashMap<>();
    private static final int MAX_QUEUED = 20;

    /** A job waiting in a player's queue. */
    private record Pending(Kind kind, BlockPos pos, @Nullable String url, @Nullable Path uploaded, String uploadName,
                           @Nullable SongLibrary.Entry replace) {
        /** Uploaded files that will not be converted any more are deleted. */
        void discard() {
            if (uploaded != null) {
                AudioTools.deleteTree(uploaded.getParent());
            }
        }
    }

    private enum Kind {
        YT_DLP, DIRECT_FILE, UPLOAD
    }

    private SongDownloads() {
    }

    public static boolean isRunning(ServerPlayer player) {
        return RUNNING.contains(player.getUUID());
    }

    public static void stop() {
        RUNNING.clear();
        QUEUES.values().forEach(queue -> queue.forEach(Pending::discard));
        QUEUES.clear();
    }

    /** Jobs of the player waiting behind the running one. */
    public static int queued(ServerPlayer player) {
        Deque<Pending> queue = QUEUES.get(player.getUUID());
        return queue == null ? 0 : queue.size();
    }

    /** Starts the job now, or queues it behind the player's running one. */
    private static void enqueue(ServerPlayer player, Pending job) {
        UUID playerId = player.getUUID();
        if (!RUNNING.contains(playerId)) {
            launch(player, job);
            return;
        }
        Deque<Pending> queue = QUEUES.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        if (queue.size() >= MAX_QUEUED) {
            job.discard();
            CreeperServer.status(player, job.pos(), tr("queue_full", MAX_QUEUED), -1);
            return;
        }
        queue.addLast(job);
        CreeperServer.status(player, job.pos(), tr("queued", queue.size()), -1);
    }

    /** The running job of the player ended: start the next one (checks again; offline players lose their queue). */
    private static void next(MinecraftServer server, UUID playerId) {
        Deque<Pending> queue = QUEUES.get(playerId);
        if (queue == null || RUNNING.contains(playerId)) {
            return;
        }
        if (queue.isEmpty()) {
            QUEUES.remove(playerId);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            queue.forEach(Pending::discard);
            QUEUES.remove(playerId);
            return;
        }
        Pending job = queue.pollFirst();
        if (queue.isEmpty()) {
            QUEUES.remove(playerId);
        }
        if (!mayAdd(player, job.pos(), CreeperConfig.get())) {
            job.discard();
            next(server, playerId);
            return;
        }
        launch(player, job);
    }

    /** Status text with the number of waiting jobs, e.g. "Downloading... 40 % (+2 waiting)". */
    private static Component withQueue(Component text, UUID playerId) {
        Deque<Pending> queue = QUEUES.get(playerId);
        if (queue == null || queue.isEmpty()) {
            return text;
        }
        return Component.empty().append(text).append(" ").append(tr("queue_more", queue.size()));
    }

    /** A new song from a link. */
    public static void start(ServerPlayer player, BlockPos pos, String link) {
        CreeperConfig config = CreeperConfig.get();
        if (!mayAdd(player, pos, config)) {
            return;
        }
        String url = normalize(link);
        if (url == null) {
            CreeperServer.status(player, pos, tr("invalid_link"), -1);
            return;
        }
        Kind kind = kind(URI.create(url), config);
        if (kind == null) {
            CreeperServer.status(player, pos, tr("host_not_allowed", URI.create(url).getHost()), -1);
            return;
        }
        enqueue(player, new Pending(kind, pos, url, null, "", null));
    }

    /** Downloads a song again from its link, in the current quality; it keeps its title, adder and blasters. */
    public static void redownload(ServerPlayer player, BlockPos pos, SongLibrary.Entry song) {
        CreeperConfig config = CreeperConfig.get();
        boolean mine = song.ownedBy(player.getUUID()) || CreeperPermission.DELETE_ANY.has(player);
        if (!mine) {
            CreeperServer.status(player, pos, tr("no_permission"), -1);
            return;
        }
        if (!mayAdd(player, pos, config)) {
            return;
        }
        Kind kind = song.source.isEmpty() ? null : kind(URI.create(song.source), config);
        if (kind == null) {
            CreeperServer.status(player, pos, tr("no_source"), -1);
            return;
        }
        enqueue(player, new Pending(kind, pos, song.source, null, "", song));
    }

    /** A local file the player uploaded (complete in {@code file}; its folder is deleted afterwards). */
    public static void upload(ServerPlayer player, BlockPos pos, Path file, String fileName) {
        enqueue(player, new Pending(Kind.UPLOAD, pos, null, file, fileName, null));
    }

    /** Checks shared by links and uploads: downloads on, permission, quota and daily limit not used up. */
    static boolean mayAdd(ServerPlayer player, BlockPos pos, CreeperConfig config) {
        SongLibrary library = SongLibrary.get();
        if (library == null) {
            return false;
        }
        if (!config.allowDownloads) {
            CreeperServer.status(player, pos, tr("disabled"), -1);
            return false;
        }
        if (!CreeperPermission.DOWNLOAD.has(player)) {
            CreeperServer.status(player, pos, tr("no_permission"), -1);
            return false;
        }
        long quota = config.quotaBytes(player);
        long used = library.usedBytes(player.getUUID());
        if (quota >= 0 && used >= quota) {
            CreeperServer.status(player, pos, tr("quota_full", mib(used), mib(quota)), -1);
            return false;
        }
        long dailyLimit = config.dailyLimitBytes(player);
        if (dailyLimit >= 0 && library.dailyBytes(player.getUUID()) >= dailyLimit) {
            CreeperServer.status(player, pos, tr("daily_full", mib(dailyLimit)), -1);
            return false;
        }
        return true;
    }

    private static void launch(ServerPlayer player, Pending pending) {
        MinecraftServer server = player.server;
        SongLibrary library = SongLibrary.get();
        if (library == null) {
            pending.discard();
            return;
        }
        BlockPos pos = pending.pos();
        Kind kind = pending.kind();
        String url = pending.url();
        Path uploaded = pending.uploaded();
        String uploadName = pending.uploadName();
        // A queued re-download of a song that was deleted in the meantime is dropped.
        SongLibrary.Entry replace = pending.replace() == null ? null : library.get(pending.replace().id);
        if (pending.replace() != null && replace == null) {
            CreeperServer.status(player, pos, tr("song_gone"), -1);
            next(server, player.getUUID());
            return;
        }
        CreeperConfig config = CreeperConfig.get();
        UUID playerId = player.getUUID();
        RUNNING.add(playerId);
        // The song must still fit afterwards (checked when its size is known). A re-download frees the old file;
        // replacing another player's song (delete_any) counts against nobody's quota.
        long room = Long.MAX_VALUE;
        if (replace == null || replace.ownedBy(playerId)) {
            long quota = config.quotaBytes(player);
            if (quota >= 0) {
                room = quota - library.usedBytes(playerId) + (replace == null ? 0 : replace.bytes);
            }
        }
        long dailyLimit = config.dailyLimitBytes(player);
        if (dailyLimit >= 0) {
            room = Math.min(room, dailyLimit - library.dailyBytes(playerId));
        }

        int generation = CreeperWorker.generation();
        ResourceKey<Level> dimension = player.level().dimension();
        Job job = new Job(kind, url, uploaded, uploadName, library.root(), config.maxSongMinutes * 60, room,
                player.getGameProfile().getName(), playerId, step -> server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null && generation == CreeperWorker.generation() && RUNNING.contains(playerId)) {
                CreeperServer.status(online, pos, withQueue(step < 0 ? tr("preparing")
                        : step > 100 ? tr("converting") : tr("downloading", step), playerId),
                        Math.max(0, Math.min(100, step)));
            }
        }));
        CreeperServer.status(player, pos, withQueue(tr(kind == Kind.UPLOAD ? "converting" : "preparing"), playerId),
                0);

        CompletableFuture<SongLibrary.Entry> future = CreeperWorker.download(job::run);
        future.whenComplete((entry, error) -> server.execute(() -> {
            RUNNING.remove(playerId);
            if (generation != CreeperWorker.generation()) {
                return;
            }
            try {
                finish(server, playerId, pos, dimension, url, uploadName, replace, entry, error);
            } finally {
                next(server, playerId);
            }
        }));
    }

    private static void finish(MinecraftServer server, UUID playerId, BlockPos pos, ResourceKey<Level> dimension,
                               @Nullable String url, String uploadName, @Nullable SongLibrary.Entry replace,
                               @Nullable SongLibrary.Entry entry, @Nullable Throwable error) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        SongLibrary current = SongLibrary.get();
        if (error != null || entry == null || current == null) {
            Component message = error != null && error.getCause() instanceof Failure failure ? failure.message
                    : tr("failed", String.valueOf(error));
            if (error != null && !(error.getCause() instanceof Failure)) {
                LOGGER.warn("CreeperBlaster3000 download of {} failed", url == null ? uploadName : url, error);
            }
            if (online != null) {
                CreeperServer.status(online, pos, message, -1);
            }
            return;
        }
        current.recordDownload(playerId, entry.bytes);
        SongLibrary.Entry song;
        Component message;
        SongLibrary.Entry old = replace == null ? null : current.get(replace.id);
        if (old != null) {
            // Same song, new file: keeps its name, adder and age.
            entry.title = old.title;
            entry.artist = old.artist.isEmpty() ? entry.artist : old.artist;
            entry.addedBy = old.addedBy;
            entry.addedById = old.addedById;
            entry.addedAt = old.addedAt;
            entry.legacyName = old.legacyName;
            current.replace(old, entry);
            CreeperChannels.songReplaced(server, old.id, entry);
            song = entry;
            message = tr("redownloaded", entry.title);
        } else {
            SongLibrary.Entry existing = current.get(entry.id);
            if (existing == null) {
                current.add(entry);
            }
            song = existing != null ? existing : entry;
            message = existing != null ? tr("already_there", song.title) : tr("done", song.title);
        }
        CreeperServer.libraryChanged(server);
        if (online != null) {
            CreeperServer.status(online, pos, message, -1);
        }
        // A new song is selected on the blaster unless it is playing something.
        ServerLevel level = server.getLevel(dimension);
        if (old == null && level != null && level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof CreeperBlaster3000BlockEntity blaster
                && !blaster.isPlaying()) {
            blaster.selectSong(song);
        }
    }

    /** yt-dlp for the configured hosts, our own download for direct audio files elsewhere, null when not allowed. */
    @Nullable
    private static Kind kind(URI uri, CreeperConfig config) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String allowed : config.ytDlpHosts) {
            String entry = allowed.trim().toLowerCase(Locale.ROOT);
            if (!entry.isEmpty() && (host.equals(entry) || host.endsWith("." + entry))) {
                return Kind.YT_DLP;
            }
        }
        if (config.allowDirectAudioLinks && uri.getPath() != null) {
            String path = uri.getPath().toLowerCase(Locale.ROOT);
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && path.lastIndexOf('/') < dot
                    && config.directAudioExtensions.contains(path.substring(dot + 1))) {
                return Kind.DIRECT_FILE;
            }
        }
        return null;
    }

    /** https:// is added when missing; only http(s) links with a host are allowed. */
    @Nullable
    static String normalize(String link) {
        String url = link.trim();
        if (url.isEmpty() || url.length() > 2000 || url.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? url : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Only public internet addresses: no localhost or private/link-local networks, so players cannot make the server
     * fetch from its own network.
     */
    static boolean isPublicHost(@Nullable String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] bytes = address.getAddress();
                boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress() || uniqueLocal) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static String mib(long bytes) {
        return String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.ymtechnicals.creeper_blaster.download." + key, args);
    }

    private record Tags(String title, String artist) {
    }

    /** A download that failed for a reason the player should read. */
    private static final class Failure extends RuntimeException {
        private final Component message;

        private Failure(Component message) {
            super(message.getString(), null, false, false);
            this.message = message;
        }
    }

    /**
     * @param room     bytes the song may have at most (quota and daily limit), Long.MAX_VALUE without limits
     * @param progress -1 = preparing, 0..100 = downloading, 101 = converting
     */
    private record Job(Kind kind, @Nullable String url, @Nullable Path uploaded, String uploadName, Path root,
                       int maxSeconds, long room, String playerName, UUID playerId, IntConsumer progress) {
        SongLibrary.Entry run() {
            Path work = root.resolve("temp").resolve(UUID.randomUUID().toString());
            try {
                Files.createDirectories(work);
                progress.accept(kind == Kind.UPLOAD ? 101 : -1);
                if (kind != Kind.UPLOAD && !isPublicHost(URI.create(url).getHost())) {
                    throw new Failure(tr("invalid_link"));
                }
                Path ogg = work.resolve("song.ogg");
                String title = "";
                String artist = "";
                String source = kind == Kind.UPLOAD ? "" : url;
                if (kind == Kind.UPLOAD) {
                    convert(uploaded, ogg);
                    Tags tags = readTags(uploaded);
                    title = tags.title().isBlank() ? uploadName.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "")
                            : tags.title();
                    artist = tags.artist();
                } else if (kind == Kind.YT_DLP) {
                    runYtDlp(work);
                    Path infoJson = work.resolve("song.info.json");
                    if (Files.isRegularFile(infoJson)) {
                        JsonObject json = JsonParser.parseString(Files.readString(infoJson, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        title = text(json, "track", text(json, "title", ""));
                        artist = text(json, "artist", text(json, "uploader", text(json, "channel", "")));
                        source = text(json, "webpage_url", url);
                    }
                } else {
                    Path file = downloadDirect(work);
                    progress.accept(101);
                    convert(file, ogg);
                    Tags tags = readTags(file);
                    title = tags.title();
                    artist = tags.artist();
                }
                if (!Files.isRegularFile(ogg)) {
                    throw new Failure(tr("failed", "no audio"));
                }
                OggInfo.Info info = OggInfo.read(ogg);
                if (info.seconds() > maxSeconds + 5) {
                    throw new Failure(tr("too_long", maxSeconds / 60));
                }
                long bytes = Files.size(ogg);
                if (bytes > room) {
                    throw new Failure(tr("no_room", mib(bytes)));
                }
                if (title.isBlank()) {
                    String path = URI.create(url).getPath();
                    String name = path == null || path.isBlank() ? url : path.substring(path.lastIndexOf('/') + 1);
                    title = URLDecoder.decode(name, StandardCharsets.UTF_8).replaceFirst("\\.[A-Za-z0-9]{1,5}$", "");
                }
                String id = SongLibrary.moveIn(root, ogg);
                SongLibrary.Entry entry = new SongLibrary.Entry();
                entry.id = id;
                entry.title = cut(title, 200);
                entry.artist = cut(artist, 100);
                entry.addedBy = playerName;
                entry.addedById = playerId.toString();
                entry.seconds = info.seconds();
                entry.ticks = info.ticks();
                entry.bytes = bytes;
                entry.addedAt = System.currentTimeMillis();
                entry.source = source;
                return entry;
            } catch (Failure failure) {
                throw failure;
            } catch (AudioTools.Missing missing) {
                throw new Failure(missing.notAllowed
                        ? tr("tool_not_allowed", missing.tool, AudioTools.toolsFolder().toString())
                        : tr("no_tools", missing.tool, missing.getMessage()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new Failure(tr("failed", "interrupted"));
            } catch (Exception exception) {
                LOGGER.warn("CreeperBlaster3000 download of {} failed", url, exception);
                throw new Failure(tr("failed", exception.getMessage()));
            } finally {
                AudioTools.deleteTree(work);
                if (uploaded != null) {
                    AudioTools.deleteTree(uploaded.getParent());
                }
            }
        }

        private void runYtDlp(Path work) throws IOException, InterruptedException {
            String ytDlp = AudioTools.ytDlp();
            Path ffmpegFolder = AudioTools.ffmpeg();
            List<String> command = new ArrayList<>(List.of(ytDlp,
                    // No user/system yt-dlp config (it could run commands), no generic web page extractor.
                    "--ignore-config", "--use-extractors", "default,-generic",
                    "--no-playlist", "--newline", "--no-mtime",
                    "--extract-audio", "--audio-format", "vorbis",
                    "--postprocessor-args", "ExtractAudio:" + String.join(" ", AUDIO_FORMAT),
                    "--match-filter", "!is_live & duration <=? " + maxSeconds,
                    "--max-filesize", String.valueOf(MAX_SOURCE_BYTES),
                    "--write-info-json",
                    "--output", work.resolve("song.%(ext)s").toString()));
            if (ffmpegFolder != null) {
                command.addAll(List.of("--ffmpeg-location", ffmpegFolder.toString()));
            }
            // "--" ends the options, so a link can never be read as one.
            command.addAll(List.of("--", url));
            Deque<String> lastLines = run(command, true);
            if (!Files.isRegularFile(work.resolve("song.ogg"))) {
                if (String.join("\n", lastLines).contains("does not pass filter")) {
                    throw new Failure(tr("too_long", maxSeconds / 60));
                }
                throw new Failure(tr("failed", lastError(lastLines)));
            }
        }

        /** Downloads a direct audio link; every redirect must stay on public addresses. */
        private Path downloadDirect(Path work) throws IOException, InterruptedException {
            Path target = work.resolve("source");
            try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(20)).build()) {
                URI uri = URI.create(url);
                for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                    if (!(scheme.equals("http") || scheme.equals("https")) || !isPublicHost(uri.getHost())) {
                        throw new Failure(tr("invalid_link"));
                    }
                    HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofMinutes(TIMEOUT_MINUTES)).GET().build(),
                            HttpResponse.BodyHandlers.ofInputStream());
                    int status = response.statusCode();
                    if (status / 100 == 3) {
                        response.body().close();
                        String location = response.headers().firstValue("location").orElse(null);
                        if (location == null) {
                            throw new Failure(tr("failed", "HTTP " + status));
                        }
                        uri = uri.resolve(location);
                        continue;
                    }
                    try (InputStream input = response.body()) {
                        if (status / 100 != 2) {
                            throw new Failure(tr("failed", "HTTP " + status));
                        }
                        String type = response.headers().firstValue("content-type").orElse("")
                                .toLowerCase(Locale.ROOT);
                        if (type.startsWith("text/") || type.contains("html") || type.contains("json")) {
                            throw new Failure(tr("not_audio"));
                        }
                        long length = response.headers().firstValueAsLong("content-length").orElse(-1);
                        if (length > MAX_SOURCE_BYTES) {
                            throw new Failure(tr("too_big"));
                        }
                        copy(input, target, length);
                    }
                    return target;
                }
            }
            throw new Failure(tr("failed", "too many redirects"));
        }

        private void copy(InputStream input, Path target, long length) throws IOException {
            try (OutputStream output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                int lastPercent = -1;
                int read;
                while ((read = input.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_SOURCE_BYTES) {
                        throw new Failure(tr("too_big"));
                    }
                    output.write(buffer, 0, read);
                    if (length > 0) {
                        int percent = (int) (total * 100 / length);
                        if (percent / 5 != lastPercent / 5) {
                            lastPercent = percent;
                            progress.accept(percent);
                        }
                    }
                }
            }
        }

        /** Title and artist from the file's own tags (ID3, Vorbis comments, ...); empty when it has none. */
        private Tags readTags(Path file) {
            try {
                List<String> command = List.of(AudioTools.ffprobeCommand(), "-v", "error",
                        "-protocol_whitelist", "file", "-format_whitelist", AUDIO_CONTAINERS,
                        "-show_entries", "format_tags", "-of", "json", file.toString());
                Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                CreeperWorker.track(process);
                String output;
                try {
                    output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    process.waitFor(1, TimeUnit.MINUTES);
                } finally {
                    CreeperWorker.untrack(process);
                }
                JsonObject format = JsonParser.parseString(output).getAsJsonObject().getAsJsonObject("format");
                JsonObject tags = format == null ? null : format.getAsJsonObject("tags");
                if (tags == null) {
                    return new Tags("", "");
                }
                return new Tags(tag(tags, "title"), tag(tags, "artist").isEmpty() ? tag(tags, "album_artist")
                        : tag(tags, "artist"));
            } catch (Exception exception) {
                // Tags are optional: the file name is the title then.
                LOGGER.debug("Could not read the tags of {}", file, exception);
                return new Tags("", "");
            }
        }

        /** A tag, whatever case its key has (ID3 "artist", Vorbis "ARTIST", ...). */
        private static String tag(JsonObject tags, String key) {
            for (String name : tags.keySet()) {
                if (name.equalsIgnoreCase(key) && tags.get(name).isJsonPrimitive()) {
                    return tags.get(name).getAsString().trim();
                }
            }
            return "";
        }

        /** Any audio file -> the library format. ffmpeg may only read local files (no links inside playlists). */
        private void convert(Path source, Path ogg) throws IOException, InterruptedException {
            List<String> command = new ArrayList<>(List.of(AudioTools.ffmpegCommand(), "-hide_banner", "-nostdin",
                    // Local files only, and only real audio containers (no playlists or concat lists that could
                    // pull in other files of the server).
                    "-y", "-protocol_whitelist", "file", "-format_whitelist", AUDIO_CONTAINERS,
                    "-i", source.toString(), "-vn", "-map_metadata", "-1",
                    "-t", String.valueOf(maxSeconds + 5), "-c:a", "libvorbis"));
            command.addAll(AUDIO_FORMAT);
            command.add(ogg.toString());
            Deque<String> lastLines = run(command, false);
            if (!Files.isRegularFile(ogg)) {
                throw new Failure(tr("not_audio_detail", lastError(lastLines)));
            }
        }

        private Deque<String> run(List<String> command, boolean ytDlpProgress) throws IOException,
                InterruptedException {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            CreeperWorker.track(process);
            // Kills a hanging process; reading the output below then ends.
            CompletableFuture.delayedExecutor(TIMEOUT_MINUTES, TimeUnit.MINUTES).execute(() -> {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            });
            Deque<String> lastLines = new ArrayDeque<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                    StandardCharsets.UTF_8))) {
                int lastPercent = -1;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (ytDlpProgress) {
                        Matcher matcher = PERCENT.matcher(line);
                        if (matcher.find()) {
                            int percent = (int) Double.parseDouble(matcher.group(1));
                            // Every 5 % is enough for the progress bar.
                            if (percent / 5 != lastPercent / 5) {
                                lastPercent = percent;
                                progress.accept(percent);
                            }
                        } else if (line.contains("[ExtractAudio]")) {
                            progress.accept(101);
                        }
                    }
                    lastLines.addLast(line);
                    if (lastLines.size() > 12) {
                        lastLines.removeFirst();
                    }
                }
                process.waitFor();
            } finally {
                CreeperWorker.untrack(process);
            }
            return lastLines;
        }

        private static String lastError(Deque<String> lines) {
            String error = "";
            for (String line : lines) {
                if (line.startsWith("ERROR:")) {
                    error = line.substring(6).trim();
                }
            }
            if (error.isEmpty() && !lines.isEmpty()) {
                error = lines.getLast();
            }
            return cut(error, 160);
        }

        private static String text(JsonObject json, String key, String fallback) {
            return json.has(key) && json.get(key).isJsonPrimitive() && !json.get(key).getAsString().isBlank()
                    ? json.get(key).getAsString().trim() : fallback;
        }

        private static String cut(String text, int length) {
            return text.length() <= length ? text : text.substring(0, length - 1) + "…";
        }
    }
}
