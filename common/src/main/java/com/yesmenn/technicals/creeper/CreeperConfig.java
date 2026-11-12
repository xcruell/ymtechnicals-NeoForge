package com.yesmenn.technicals.creeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server settings of the Creeper system in {@code config/ymtechnicals-server.json} (same file on both loaders). Read
 * when the server starts; missing values get their defaults and the file is written back. Whether yt-dlp and ffmpeg
 * may be downloaded is in {@link ToolsConfig}.
 */
public final class CreeperConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "ymtechnicals-server.json";
    private static volatile CreeperConfig current = new CreeperConfig();

    /** Players may add songs to the library by link. */
    public boolean allowDownloads = true;
    /**
     * Permission level for adding songs and deleting the own ones (0 = every player, 2 = operators), when no
     * permission mod decides the node ymtechnicals.creeperblaster.download.
     */
    public int downloadPermissionLevel = 2;
    /** Hosts whose pages yt-dlp reads (subdomains included), e.g. YouTube. */
    public List<String> ytDlpHosts = new ArrayList<>(List.of("youtube.com", "youtu.be"));
    /** Links to other hosts are allowed when they point straight at an audio file with one of these endings. */
    public boolean allowDirectAudioLinks = true;
    public List<String> directAudioExtensions = new ArrayList<>(List.of("mp3", "wav", "ogg", "oga", "flac", "m4a",
            "aac", "opus", "webm"));
    /**
     * Players may add audio files from their own computer (same permission, quota and limits as links): "auto" (on in
     * single player and LAN worlds, off on dedicated servers), "true" or "false".
     */
    public String allowUploads = "auto";
    /** Largest file that can be uploaded, in MiB. */
    public int maxUploadMiB = 100;
    /** Longest song that can be downloaded, in minutes. */
    public int maxSongMinutes = 15;
    /** Library space each player may fill with own songs, in MiB (0 = no limit). */
    public int playerQuotaMiB = 1024;
    /** The same for operators (permission ymtechnicals.creeperblaster.operator_limits), in MiB (0 = no limit). */
    public int operatorQuotaMiB = 51200;
    /** Download volume per player within 24 hours, in MiB (0 = no limit). */
    public int playerDailyLimitMiB = 0;
    /** The same for operators, in MiB (0 = no limit). */
    public int operatorDailyLimitMiB = 0;
    /** Upload speed per player for sending songs to the clients, in KiB per second. */
    public int transferKiBPerSecond = 512;

    public static CreeperConfig get() {
        return current;
    }

    public boolean uploadsAllowed(MinecraftServer server) {
        String value = allowUploads == null ? "auto" : allowUploads.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> !server.isDedicatedServer();
        };
    }

    /** Storage quota of a player in bytes, or -1 without a limit. */
    public long quotaBytes(ServerPlayer player) {
        int mib = CreeperPermission.OPERATOR_LIMITS.has(player) ? operatorQuotaMiB : playerQuotaMiB;
        return mib <= 0 ? -1 : mib * 1024L * 1024L;
    }

    /** Download volume a player may use within 24 hours in bytes, or -1 without a limit. */
    public long dailyLimitBytes(ServerPlayer player) {
        int mib = CreeperPermission.OPERATOR_LIMITS.has(player) ? operatorDailyLimitMiB : playerDailyLimitMiB;
        return mib <= 0 ? -1 : mib * 1024L * 1024L;
    }

    public static void load() {
        Path path = Platform.configDir().resolve(FILE);
        CreeperConfig config = null;
        if (Files.isRegularFile(path)) {
            try {
                config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), CreeperConfig.class);
            } catch (Exception exception) {
                LOGGER.warn("Could not read {}, using the defaults", path, exception);
            }
        }
        if (config == null) {
            config = new CreeperConfig();
        }
        config.clamp();
        current = config;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not write {}", path, exception);
        }
    }

    private void clamp() {
        downloadPermissionLevel = Math.max(0, Math.min(4, downloadPermissionLevel));
        maxSongMinutes = Math.max(1, Math.min(120, maxSongMinutes));
        maxUploadMiB = Math.max(1, Math.min(2048, maxUploadMiB));
        if (ytDlpHosts == null) {
            ytDlpHosts = new ArrayList<>();
        }
        if (directAudioExtensions == null) {
            directAudioExtensions = new ArrayList<>();
        }
        transferKiBPerSecond = Math.max(32, Math.min(16384, transferKiBPerSecond));
    }
}
