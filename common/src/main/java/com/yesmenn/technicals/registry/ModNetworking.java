package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.block.entity.Soundeffectz3000BlockEntity;
import com.yesmenn.technicals.client.ObserversEyeClientHooks;
import com.yesmenn.technicals.client.Soundeffectz3000ClientHooks;
import com.yesmenn.technicals.creeper.client.CreeperClientPackets;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import com.yesmenn.technicals.creeper.server.CreeperServer;
import com.yesmenn.technicals.network.ObserversEyeActionPayload;
import com.yesmenn.technicals.network.OpenObserversEyeScreenPayload;
import com.yesmenn.technicals.network.OpenSoundeffectz3000ScreenPayload;
import com.yesmenn.technicals.network.Soundeffectz3000ActionPayload;
import com.yesmenn.technicals.network.Soundeffectz3000PlaybackPayload;
import com.yesmenn.technicals.network.Soundeffectz3000StopPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * All packets of the mod, for both loaders: each loader registers these lists with its own network API and calls the
 * handlers on the main thread. Client handlers are lambdas that only call into client classes when they run, so a
 * dedicated server never loads them.
 */
public final class ModNetworking {
    public static final String PROTOCOL_VERSION = "4";

    public record ToClient<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                          Consumer<T> handler) {
    }

    public record ToServer<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                          BiConsumer<T, ServerPlayer> handler) {
    }

    private ModNetworking() {
    }

    public static List<ToClient<?>> toClient() {
        return List.of(
                new ToClient<>(OpenSoundeffectz3000ScreenPayload.TYPE, OpenSoundeffectz3000ScreenPayload.STREAM_CODEC,
                        payload -> Soundeffectz3000ClientHooks.openScreen(payload)),
                new ToClient<>(Soundeffectz3000PlaybackPayload.TYPE, Soundeffectz3000PlaybackPayload.STREAM_CODEC,
                        payload -> Soundeffectz3000ClientHooks.play(payload)),
                new ToClient<>(Soundeffectz3000StopPayload.TYPE, Soundeffectz3000StopPayload.STREAM_CODEC,
                        payload -> Soundeffectz3000ClientHooks.stop(payload)),
                new ToClient<>(OpenObserversEyeScreenPayload.TYPE, OpenObserversEyeScreenPayload.STREAM_CODEC,
                        payload -> ObserversEyeClientHooks.openScreen(payload)),
                new ToClient<>(CreeperPayloads.Channel.TYPE, CreeperPayloads.Channel.STREAM_CODEC,
                        payload -> CreeperClientPackets.channel(payload)),
                new ToClient<>(CreeperPayloads.SongChunk.TYPE, CreeperPayloads.SongChunk.STREAM_CODEC,
                        payload -> CreeperClientPackets.songChunk(payload)),
                new ToClient<>(CreeperPayloads.Library.TYPE, CreeperPayloads.Library.STREAM_CODEC,
                        payload -> CreeperClientPackets.library(payload)),
                new ToClient<>(CreeperPayloads.BlasterStatus.TYPE, CreeperPayloads.BlasterStatus.STREAM_CODEC,
                        payload -> CreeperClientPackets.status(payload)),
                new ToClient<>(CreeperPayloads.UploadAck.TYPE, CreeperPayloads.UploadAck.STREAM_CODEC,
                        payload -> CreeperClientPackets.uploadAck(payload)));
    }

    public static List<ToServer<?>> toServer() {
        return List.of(
                new ToServer<>(Soundeffectz3000ActionPayload.TYPE, Soundeffectz3000ActionPayload.STREAM_CODEC,
                        ModNetworking::handleSoundeffectzAction),
                new ToServer<>(ObserversEyeActionPayload.TYPE, ObserversEyeActionPayload.STREAM_CODEC,
                        ModNetworking::handleObserversEyeAction),
                new ToServer<>(CreeperPayloads.SongRequest.TYPE, CreeperPayloads.SongRequest.STREAM_CODEC,
                        CreeperServer::handle),
                new ToServer<>(CreeperPayloads.BlasterAction.TYPE, CreeperPayloads.BlasterAction.STREAM_CODEC,
                        CreeperServer::handle),
                new ToServer<>(CreeperPayloads.SpeakerSettings.TYPE, CreeperPayloads.SpeakerSettings.STREAM_CODEC,
                        CreeperServer::handle),
                new ToServer<>(CreeperPayloads.UploadStart.TYPE, CreeperPayloads.UploadStart.STREAM_CODEC,
                        CreeperServer::handle),
                new ToServer<>(CreeperPayloads.UploadChunk.TYPE, CreeperPayloads.UploadChunk.STREAM_CODEC,
                        CreeperServer::handle));
    }

    private static void handleObserversEyeAction(ObserversEyeActionPayload payload, ServerPlayer player) {
        if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D
                || !(player.level().getBlockEntity(payload.pos()) instanceof ObserversEyeBlockEntity sensor)) {
            return;
        }
        sensor.applySettings(
                payload.sizeX(),
                payload.sizeY(),
                payload.sizeZ(),
                payload.offsetX(),
                payload.offsetY(),
                payload.offsetZ(),
                payload.pattern(),
                payload.outputMode(),
                payload.filterMode(),
                payload.preview(),
                payload.players());
    }

    private static void handleSoundeffectzAction(Soundeffectz3000ActionPayload payload, ServerPlayer player) {
        if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D
                || !(player.level().getBlockEntity(payload.pos()) instanceof Soundeffectz3000BlockEntity soundBlock)) {
            return;
        }
        boolean changed = soundBlock.applySettings(
                payload.soundId(),
                payload.volume(),
                payload.pitch(),
                payload.range(),
                payload.looping(),
                payload.redstoneEnabled());
        if (payload.action() == Soundeffectz3000ActionPayload.PLAY) {
            soundBlock.play();
        } else if (payload.action() == Soundeffectz3000ActionPayload.STOP) {
            soundBlock.stop();
        } else if (payload.action() == Soundeffectz3000ActionPayload.SAVE && changed) {
            // Closing the screen without changes must not restart a running loop.
            soundBlock.refreshPlayingSound();
        }
    }
}
