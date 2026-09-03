package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.block.entity.Soundeffectz3000BlockEntity;
import com.yesmenn.technicals.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Soundeffectz3000ActionPayload.TYPE, Soundeffectz3000ActionPayload.STREAM_CODEC, ModNetworking::handleSoundeffectzAction);
        registrar.playToClient(OpenSoundeffectz3000ScreenPayload.TYPE, OpenSoundeffectz3000ScreenPayload.STREAM_CODEC, ModNetworking::handleOpenSoundeffectzScreen);
        registrar.playToClient(Soundeffectz3000PlaybackPayload.TYPE, Soundeffectz3000PlaybackPayload.STREAM_CODEC, ModNetworking::handleSoundeffectzPlayback);
        registrar.playToClient(Soundeffectz3000StopPayload.TYPE, Soundeffectz3000StopPayload.STREAM_CODEC, ModNetworking::handleSoundeffectzStop);
        registrar.playToServer(ObserversEyeActionPayload.TYPE, ObserversEyeActionPayload.STREAM_CODEC, ModNetworking::handleObserversEyeAction);
        registrar.playToClient(OpenObserversEyeScreenPayload.TYPE, OpenObserversEyeScreenPayload.STREAM_CODEC, ModNetworking::handleOpenObserversEyeScreen);
    }

    private static void handleObserversEyeAction(
            ObserversEyeActionPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0D
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

    private static void handleOpenObserversEyeScreen(
            OpenObserversEyeScreenPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class<?> hooks = Class.forName("com.yesmenn.technicals.client.ObserversEyeClientHooks");
            hooks.getMethod("openScreen", OpenObserversEyeScreenPayload.class).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open Observer's Eye screen.", exception);
        }
    }

    private static void handleSoundeffectzAction(
            Soundeffectz3000ActionPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0D
                || !(player.level().getBlockEntity(payload.pos()) instanceof Soundeffectz3000BlockEntity soundBlock)) {
            return;
        }
        soundBlock.applySettings(
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
        } else if (payload.action() == Soundeffectz3000ActionPayload.SAVE) {
            soundBlock.refreshPlayingSound();
        }
    }

    private static void handleOpenSoundeffectzScreen(
            OpenSoundeffectz3000ScreenPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        invokeSoundeffectzClient("openScreen", OpenSoundeffectz3000ScreenPayload.class, payload);
    }

    private static void handleSoundeffectzPlayback(
            Soundeffectz3000PlaybackPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        invokeSoundeffectzClient("play", Soundeffectz3000PlaybackPayload.class, payload);
    }

    private static void handleSoundeffectzStop(
            Soundeffectz3000StopPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        invokeSoundeffectzClient("stop", Soundeffectz3000StopPayload.class, payload);
    }


    private static void invokeSoundeffectzClient(String method, Class<?> parameterType, Object payload) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class<?> hooks = Class.forName("com.yesmenn.technicals.client.Soundeffectz3000ClientHooks");
            hooks.getMethod(method, parameterType).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not handle Soundeffectz3000 client action.", exception);
        }
    }


}
