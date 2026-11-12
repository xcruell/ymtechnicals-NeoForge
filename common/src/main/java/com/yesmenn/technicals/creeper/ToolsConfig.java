package com.yesmenn.technicals.creeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.platform.Platform;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether YM Technicals may download yt-dlp and ffmpeg for the CreeperBlaster3000 and keep yt-dlp up to date
 * ({@code config/ymtechnicals-tools.json}). Nothing is downloaded before {@link #confirmed}: on the client the setup
 * dialog in the main menu asks once; a dedicated server admin sets it in the file.
 */
public final class ToolsConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "ymtechnicals-tools.json";
    private static ToolsConfig current;

    /** The player / admin agreed to the settings below (set by the setup dialog or by hand). */
    public boolean confirmed = false;
    /** Download yt-dlp when it is not on the PATH or in the tools folder. */
    public boolean installYtDlp = true;
    /** Download ffmpeg and ffprobe when they are not on the PATH or in the tools folder. */
    public boolean installFfmpeg = true;
    /** Update a yt-dlp that YM Technicals downloaded itself once per game / server start. */
    public boolean autoUpdateYtDlp = true;

    public static synchronized ToolsConfig get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    private static ToolsConfig load() {
        Path path = path();
        ToolsConfig config = null;
        if (Files.isRegularFile(path)) {
            try {
                config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ToolsConfig.class);
            } catch (Exception exception) {
                LOGGER.warn("Could not read {}, using the defaults", path, exception);
            }
        }
        if (config == null) {
            config = new ToolsConfig();
            config.write();
        }
        return config;
    }

    /** Stores changed settings (setup dialog). */
    public static synchronized void save(boolean installYtDlp, boolean installFfmpeg, boolean autoUpdateYtDlp) {
        ToolsConfig config = get();
        config.confirmed = true;
        config.installYtDlp = installYtDlp;
        config.installFfmpeg = installFfmpeg;
        config.autoUpdateYtDlp = autoUpdateYtDlp;
        config.write();
    }

    /** Reads the file again (server start: an admin may have edited it). */
    public static synchronized void reload() {
        current = load();
    }

    private void write() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not write {}", path, exception);
        }
    }

    public static Path path() {
        return Platform.configDir().resolve(FILE);
    }
}
