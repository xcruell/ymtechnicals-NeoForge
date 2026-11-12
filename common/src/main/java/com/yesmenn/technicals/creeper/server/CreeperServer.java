package com.yesmenn.technicals.creeper.server;

import com.yesmenn.technicals.creeper.CreeperConfig;
import com.yesmenn.technicals.creeper.CreeperPermission;
import com.yesmenn.technicals.creeper.ToolsConfig;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.yesmenn.technicals.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Creeper system: lifecycle, player sync and the packets from the screens. The loaders call the
 * lifecycle methods from their events.
 */
public final class CreeperServer {
    /** Players with an open blaster screen (they get library updates). */
    private static final Map<UUID, BlockPos> VIEWERS = new HashMap<>();
    private static final double MAX_DISTANCE_SQR = 10.0 * 10.0;

    private CreeperServer() {
    }

    /** Server starts, before the worlds load. */
    public static void serverStarting(MinecraftServer server) {
        CreeperConfig.load();
        ToolsConfig.reload();
        SongLibrary.start(server);
        if (server.isDedicatedServer() && !ToolsConfig.get().confirmed) {
            YMTechnicals.LOGGER.warn("CreeperBlaster3000: yt-dlp and ffmpeg are needed to add songs by link. YM Technicals"
                    + " may download them (and keep yt-dlp up to date) once you set \"confirmed\": true in {}"
                    + " (installYtDlp / installFfmpeg / autoUpdateYtDlp choose what), or install them yourself"
                    + " (PATH, or {}).", ToolsConfig.path(), AudioTools.toolsFolder());
        }
        // Installs / updates the tools in the background, so the first download does not wait for it.
        CreeperWorker.download(() -> {
            AudioTools.prepare();
            return null;
        });
    }

    public static void serverStopping() {
        CreeperWorker.stopAll();
        SongDownloads.stop();
        SongTransfers.stop();
        SongUploads.stop();
    }

    public static void serverStopped() {
        CreeperChannels.clear();
        SongLibrary.stop();
        VIEWERS.clear();
    }

    /** End of a level's tick. */
    public static void levelTicked(ServerLevel level) {
        CreeperChannels.flush(level);
    }

    /** End of a server tick. */
    public static void serverTicked(MinecraftServer server) {
        SongTransfers.tick(server);
        if (server.getTickCount() % 20 == 0) {
            SongUploads.tick();
        }
    }

    /** Joined the server, changed dimension or respawned: the client needs the channels of its dimension. */
    public static void playerEnteredLevel(ServerPlayer player) {
        CreeperChannels.sync(player);
    }

    public static void playerLeft(UUID player) {
        VIEWERS.remove(player);
        SongTransfers.forget(player);
        SongUploads.cancel(player);
    }

    /** Status line of a player's blaster screen. */
    public static void status(ServerPlayer player, BlockPos pos, Component text, int progress) {
        Platform.sendToPlayer(player, new CreeperPayloads.BlasterStatus(pos, text, progress,
                SongDownloads.isRunning(player)));
    }

    public static void libraryChanged(MinecraftServer server) {
        VIEWERS.keySet().forEach(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                sendLibrary(player);
            }
        });
    }

    /** The library for one player: which songs that player may delete, rights and limits. */
    private static void sendLibrary(ServerPlayer player) {
        SongLibrary library = SongLibrary.get();
        if (library == null) {
            return;
        }
        CreeperConfig config = CreeperConfig.get();
        boolean mayDownload = config.allowDownloads && CreeperPermission.DOWNLOAD.has(player);
        Platform.sendToPlayer(player, new CreeperPayloads.Library(
                library.list(player.getUUID(), CreeperPermission.DELETE_ANY.has(player), mayDownload), mayDownload,
                library.usedBytes(player.getUUID()), config.quotaBytes(player), library.dailyBytes(player.getUUID()),
                config.dailyLimitBytes(player), SongUploads.maxBytes(player)));
    }

    /** Own songs with the download permission, everybody's with delete_any. */
    private static boolean mayDelete(ServerPlayer player, SongLibrary.Entry entry) {
        if (CreeperPermission.DELETE_ANY.has(player)) {
            return true;
        }
        return CreeperPermission.DOWNLOAD.has(player) && entry.addedById.equals(player.getUUID().toString());
    }

    @Nullable
    private static BlockEntity reachable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.distanceToSqr(pos.getCenter()) > MAX_DISTANCE_SQR || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }

    public static void handle(CreeperPayloads.BlasterAction payload, ServerPlayer player) {
        if (payload.action() == CreeperPayloads.BlasterAction.Action.CLOSE) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        if (!(reachable(player, payload.pos()) instanceof CreeperBlaster3000BlockEntity blaster)) {
            return;
        }
        SongLibrary library = SongLibrary.get();
        switch (payload.action()) {
            case OPEN -> {
                VIEWERS.put(player.getUUID(), payload.pos());
                sendLibrary(player);
                if (!SongDownloads.isRunning(player)) {
                    status(player, payload.pos(), Component.empty(), -1);
                }
            }
            case PLAY -> blaster.play(player);
            case PAUSE -> blaster.pause();
            case STOP -> blaster.stop();
            case SEEK -> blaster.seek(payload.value());
            case SELECT -> {
                SongLibrary.Entry entry = library == null ? null : library.get(payload.text());
                if (entry != null) {
                    blaster.selectSong(entry);
                }
            }
            case DELETE -> {
                SongLibrary.Entry entry = library == null ? null : library.get(payload.text());
                if (entry == null || !mayDelete(player, entry)) {
                    return;
                }
                if (library.remove(entry.id)) {
                    if (entry.id.equals(blaster.songId())) {
                        blaster.clearSong();
                    }
                    libraryChanged(player.server);
                    status(player, payload.pos(), Component.translatable(
                            "gui.ymtechnicals.creeper_blaster.deleted", entry.title), -1);
                }
            }
            case REDOWNLOAD -> {
                SongLibrary.Entry entry = library == null ? null : library.get(payload.text());
                if (entry != null) {
                    SongDownloads.redownload(player, payload.pos(), entry);
                }
            }
            case DOWNLOAD -> {
                blaster.setLink(payload.text());
                SongDownloads.start(player, payload.pos(), payload.text());
            }
            case LINK -> blaster.setLink(payload.text());
            case CODE -> {
                if (!blaster.setCode(payload.value())) {
                    status(player, payload.pos(), Component.translatable("gui.ymtechnicals.creeper.code_taken"), -1);
                }
            }
            case RANGE -> blaster.setRange(payload.value());
            case VOLUME -> blaster.setVolume(payload.value());
            case LOOP -> blaster.setLoop(payload.value() != 0);
            default -> {
            }
        }
    }

    public static void handle(CreeperPayloads.SpeakerSettings payload, ServerPlayer player) {
        if (reachable(player, payload.pos()) instanceof CreeperSpeaker3000BlockEntity speaker) {
            speaker.apply(payload.code(), payload.range(), payload.volume());
        }
    }

    public static void handle(CreeperPayloads.UploadStart payload, ServerPlayer player) {
        if (reachable(player, payload.pos()) instanceof CreeperBlaster3000BlockEntity) {
            SongUploads.start(player, payload);
        } else {
            Platform.sendToPlayer(player, new CreeperPayloads.UploadAck(-1));
        }
    }

    public static void handle(CreeperPayloads.UploadChunk payload, ServerPlayer player) {
        SongUploads.chunk(player, payload);
    }

    public static void handle(CreeperPayloads.SongRequest payload, ServerPlayer player) {
        SongTransfers.request(player, payload.songId());
    }
}
