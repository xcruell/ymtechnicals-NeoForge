package com.yesmenn.technicals.client;

import com.yesmenn.technicals.client.screen.Soundeffectz3000Screen;
import com.yesmenn.technicals.network.OpenSoundeffectz3000ScreenPayload;
import com.yesmenn.technicals.network.Soundeffectz3000PlaybackPayload;
import com.yesmenn.technicals.network.Soundeffectz3000StopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

public final class Soundeffectz3000ClientHooks {
    private static final Map<BlockPos, ActiveSound> ACTIVE_SOUNDS = new HashMap<>();

    private Soundeffectz3000ClientHooks() {
    }

    public static void openScreen(OpenSoundeffectz3000ScreenPayload payload) {
        Minecraft.getInstance().setScreen(new Soundeffectz3000Screen(payload));
    }

    public static void play(Soundeffectz3000PlaybackPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation soundId = ResourceLocation.tryParse(payload.soundId());
        if (minecraft.level == null || soundId == null) {
            return;
        }

        ActiveSound active = ACTIVE_SOUNDS.get(payload.pos());
        if (active != null && active.sequence == payload.sequence() && !active.sound.isStopped()) {
            return;
        }
        stop(payload.pos());

        RangedSound sound = new RangedSound(payload, soundId);
        minecraft.getSoundManager().play(sound);
        ACTIVE_SOUNDS.put(payload.pos(), new ActiveSound(
                payload.sequence(), sound, System.currentTimeMillis()));
    }

    public static void stop(Soundeffectz3000StopPayload payload) {
        stop(payload.pos());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        ACTIVE_SOUNDS.entrySet().removeIf(entry -> {
            ActiveSound active = entry.getValue();
            return active.sound.isStopped()
                    || now - active.startedAtMillis > 1_000L
                    && !minecraft.getSoundManager().isActive(active.sound);
        });
    }

    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE_SOUNDS.values().forEach(active ->
                Minecraft.getInstance().getSoundManager().stop(active.sound));
        ACTIVE_SOUNDS.clear();
    }

    private static void stop(BlockPos pos) {
        ActiveSound active = ACTIVE_SOUNDS.remove(pos);
        if (active != null) {
            Minecraft.getInstance().getSoundManager().stop(active.sound);
        }
    }

    private record ActiveSound(int sequence, RangedSound sound, long startedAtMillis) {
    }

    private static final class RangedSound extends AbstractTickableSoundInstance {
        private final float baseVolume;
        private final float range;

        private RangedSound(Soundeffectz3000PlaybackPayload payload, ResourceLocation soundId) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.RECORDS, RandomSource.create());
            this.baseVolume = payload.volume() / 100.0F;
            this.range = Math.max(1.0F, payload.range());
            this.pitch = payload.pitch() / 100.0F;
            this.x = payload.pos().getX() + 0.5D;
            this.y = payload.pos().getY() + 0.5D;
            this.z = payload.pos().getZ() + 0.5D;
            this.looping = payload.looping();
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = false;
            updateVolume();
        }

        @Override
        public void tick() {
            updateVolume();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        private void updateVolume() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                volume = 0.0F;
                return;
            }
            double distance = Math.sqrt(minecraft.player.distanceToSqr(x, y, z));
            float normalized = (float) Math.max(0.0D, 1.0D - distance / range);
            volume = baseVolume * normalized * normalized;
        }
    }
}
