package com.yesmenn.technicals.creeper.server;

import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.creeper.ToolsConfig;
import com.yesmenn.technicals.platform.Platform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * yt-dlp and ffmpeg/ffprobe for the downloads. Looked for on the PATH, in the tools folder
 * ({@code ymtechnicals/creeper_blaster_3000/tools/<os-arch>} in the game / server folder) and in the old YesMenn tools
 * folder; downloaded into the tools folder only when {@link ToolsConfig} allows it (confirmed, per tool). A yt-dlp
 * that was downloaded here is updated once per start when allowed. Found tools are remembered. Worker threads only.
 */
public final class AudioTools {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(10);
    @Nullable
    private static String ytDlp;
    /** Folder with ffmpeg and ffprobe; empty string when both are on the PATH. */
    @Nullable
    private static String ffmpeg;
    private static boolean updated;

    /** A tool is missing and may not (or could not) be downloaded. */
    public static final class Missing extends IOException {
        public final String tool;
        /** Not confirmed, or the download of this tool is switched off. */
        public final boolean notAllowed;

        Missing(String tool, boolean notAllowed, String message) {
            super(message);
            this.tool = tool;
            this.notAllowed = notAllowed;
        }
    }

    private AudioTools() {
    }

    public static Path toolsFolder() {
        return Platform.gameDir().resolve("ymtechnicals").resolve("creeper_blaster_3000").resolve("tools")
                .resolve(system()).toAbsolutePath().normalize();
    }

    public static Path ffmpegFolder() {
        return toolsFolder().resolve("ffmpeg");
    }

    private static Path legacyFolder() {
        return Platform.gameDir().resolve("YesMenn").resolve("CreeperBlaster3000").resolve("tools").resolve(system());
    }

    /** Command for yt-dlp. */
    public static synchronized String ytDlp() throws IOException, InterruptedException {
        if (ytDlp != null && (ytDlp.equals("yt-dlp") || Files.isRegularFile(Path.of(ytDlp)))) {
            return ytDlp;
        }
        ytDlp = null;
        if (runs(List.of("yt-dlp", "--version"))) {
            ytDlp = "yt-dlp";
            return ytDlp;
        }
        for (Path candidate : List.of(toolsFolder().resolve(ytDlpName()), legacyFolder().resolve(ytDlpName()))) {
            if (Files.isRegularFile(candidate) && runs(List.of(candidate.toString(), "--version"))) {
                ytDlp = candidate.toString();
                return ytDlp;
            }
        }
        ToolsConfig config = ToolsConfig.get();
        if (!config.confirmed || !config.installYtDlp) {
            throw new Missing("yt-dlp", true, "yt-dlp is not installed and may not be downloaded");
        }
        Path target = toolsFolder().resolve(ytDlpName());
        try {
            fetch(ytDlpUrl(), target);
        } catch (IOException exception) {
            throw new Missing("yt-dlp", false, exception.getMessage());
        }
        target.toFile().setExecutable(true);
        if (!runs(List.of(target.toString(), "--version"))) {
            throw new Missing("yt-dlp", false, "the downloaded yt-dlp does not run on this system");
        }
        LOGGER.info("Downloaded yt-dlp to {}", target);
        // Fresh from the latest release: no update needed this start.
        updated = true;
        ytDlp = target.toString();
        return ytDlp;
    }

    /** Folder with ffmpeg and ffprobe, or null when both are on the PATH. */
    @Nullable
    public static synchronized Path ffmpeg() throws IOException, InterruptedException {
        if (ffmpeg != null) {
            if (ffmpeg.isEmpty()) {
                return null;
            }
            if (Files.isDirectory(Path.of(ffmpeg))) {
                return Path.of(ffmpeg);
            }
        }
        ffmpeg = null;
        if (runs(List.of("ffmpeg", "-version")) && runs(List.of("ffprobe", "-version"))) {
            ffmpeg = "";
            return null;
        }
        Path folder = ffmpegIn(ffmpegFolder());
        if (folder == null) {
            folder = ffmpegIn(legacyFolder().resolve("ffmpeg"));
        }
        if (folder == null) {
            ToolsConfig config = ToolsConfig.get();
            if (!config.confirmed || !config.installFfmpeg) {
                throw new Missing("ffmpeg", true, "ffmpeg is not installed and may not be downloaded");
            }
            try {
                installFfmpeg();
            } catch (IOException exception) {
                throw new Missing("ffmpeg", false, exception.getMessage());
            }
            folder = ffmpegIn(ffmpegFolder());
            if (folder == null) {
                throw new Missing("ffmpeg", false, "the downloaded ffmpeg does not run on this system");
            }
            LOGGER.info("Downloaded ffmpeg to {}", folder);
        }
        ffmpeg = folder.toString();
        return folder;
    }

    /** Command for ffmpeg itself. */
    public static String ffmpegCommand() throws IOException, InterruptedException {
        Path folder = ffmpeg();
        return folder == null ? "ffmpeg" : folder.resolve(exe("ffmpeg")).toString();
    }

    /** Command for ffprobe (next to ffmpeg). */
    public static String ffprobeCommand() throws IOException, InterruptedException {
        Path folder = ffmpeg();
        return folder == null ? "ffprobe" : folder.resolve(exe("ffprobe")).toString();
    }

    /**
     * Start of the game or server: installs what is missing (if allowed) and updates a yt-dlp that YM Technicals
     * downloaded itself (once per start, if allowed). Errors are only logged.
     */
    public static void prepare() {
        ToolsConfig config = ToolsConfig.get();
        if (!config.confirmed) {
            return;
        }
        try {
            ytDlp();
        } catch (IOException exception) {
            LOGGER.info("yt-dlp is not available: {}", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            ffmpeg();
        } catch (IOException exception) {
            LOGGER.info("ffmpeg is not available: {}", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        update();
    }

    /** Updates yt-dlp when it is our own copy (a PATH install belongs to the system and is left alone). */
    private static synchronized void update() {
        ToolsConfig config = ToolsConfig.get();
        if (updated || !config.autoUpdateYtDlp || ytDlp == null || ytDlp.equals("yt-dlp")
                || !Path.of(ytDlp).startsWith(toolsFolder())) {
            return;
        }
        updated = true;
        try {
            Process process = new ProcessBuilder(ytDlp, "-U").redirectErrorStream(true).start();
            CreeperWorker.track(process);
            try {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!process.waitFor(2, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                }
                String last = output.isEmpty() ? "" : output.substring(output.lastIndexOf('\n') + 1);
                LOGGER.info("yt-dlp update: {}", last);
            } finally {
                CreeperWorker.untrack(process);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not update yt-dlp", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Nullable
    private static Path ffmpegIn(Path folder) throws InterruptedException {
        if (!Files.isDirectory(folder)) {
            return null;
        }
        List<Path> candidates = new ArrayList<>(List.of(folder, folder.resolve("bin")));
        try (var walk = Files.walk(folder, 4)) {
            walk.filter(Files::isDirectory).forEach(candidates::add);
        } catch (IOException ignored) {
            // the two default places are enough
        }
        for (Path candidate : candidates) {
            Path ffmpegFile = candidate.resolve(exe("ffmpeg"));
            Path ffprobeFile = candidate.resolve(exe("ffprobe"));
            if (Files.isRegularFile(ffmpegFile) && Files.isRegularFile(ffprobeFile)) {
                ffmpegFile.toFile().setExecutable(true);
                ffprobeFile.toFile().setExecutable(true);
                if (runs(List.of(ffmpegFile.toString(), "-version"))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static void installFfmpeg() throws IOException, InterruptedException {
        String build = switch (system()) {
            case "windows-x64" -> "win64-gpl.zip";
            case "windows-arm64" -> "winarm64-gpl.zip";
            case "linux-x64" -> "linux64-gpl.tar.xz";
            case "linux-arm64" -> "linuxarm64-gpl.tar.xz";
            default -> throw new IOException("no ffmpeg download for " + system() + ", install ffmpeg yourself");
        };
        Path tools = toolsFolder();
        Path archive = tools.resolve("ffmpeg-" + build);
        fetch("https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-" + build,
                archive);
        Path target = ffmpegFolder();
        Files.createDirectories(target);
        try {
            if (build.endsWith(".zip")) {
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = Path.of(entry.getName()).getFileName().toString();
                        if (!entry.isDirectory() && (name.equals(exe("ffmpeg")) || name.equals(exe("ffprobe")))) {
                            Files.copy(zip, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } else {
                Path unpacked = Files.createTempDirectory(tools, "ffmpeg-");
                try {
                    Process tar = new ProcessBuilder("tar", "-xJf", archive.toString(), "-C", unpacked.toString())
                            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                    if (!tar.waitFor(5, TimeUnit.MINUTES) || tar.exitValue() != 0) {
                        tar.destroyForcibly();
                        throw new IOException("could not unpack " + archive.getFileName());
                    }
                    try (var walk = Files.walk(unpacked)) {
                        for (Path file : walk.filter(Files::isRegularFile).toList()) {
                            String name = file.getFileName().toString();
                            if (name.equals("ffmpeg") || name.equals("ffprobe")) {
                                Files.copy(file, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                } finally {
                    deleteTree(unpacked);
                }
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static String ytDlpUrl() throws IOException {
        String file = switch (system()) {
            case "windows-x64" -> "yt-dlp.exe";
            case "windows-arm64" -> "yt-dlp_arm64.exe";
            case "linux-x64" -> "yt-dlp_linux";
            case "linux-arm64" -> "yt-dlp_linux_aarch64";
            case "macos-x64", "macos-arm64" -> "yt-dlp_macos";
            default -> throw new IOException("no yt-dlp download for " + system() + ", install yt-dlp yourself");
        };
        return "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + file;
    }

    static void fetch(String url, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build()) {
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(10)).GET().build(), HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() / 100 != 2) {
                Files.deleteIfExists(part);
                throw new IOException("download of " + url + " failed: HTTP " + response.statusCode());
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean runs(List<String> command) throws InterruptedException {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(CHECK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        }
    }

    static void deleteTree(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException exception) {
            LOGGER.debug("Could not delete {}", path, exception);
        }
    }

    public static String exe(String name) {
        return windows() ? name + ".exe" : name;
    }

    public static String ytDlpName() {
        return windows() ? "yt-dlp.exe" : "yt-dlp";
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String system() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "windows" : os.contains("mac") || os.contains("darwin") ? "macos"
                : os.contains("linux") ? "linux" : os.replace(' ', '_');
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return name + (arch.contains("aarch64") || arch.contains("arm64") ? "-arm64" : "-x64");
    }
}
