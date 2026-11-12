package com.yesmenn.technicals.creeper.client;

import com.yesmenn.technicals.creeper.SablePositions;
import com.yesmenn.technicals.creeper.network.ChannelState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plays the channels on this client. Every other tick each source (blaster and speakers) of a playing channel gets a
 * loudness from its distance (full up to a fifth of its range, then a smooth fade to zero at the range); the loudest
 * ones play, each as its own positional sound, so OpenAL still pans them left and right. Their loudness is balanced
 * by total power, so two speakers are not louder than one and nothing jumps when one comes or goes; sounds fade in
 * and out instead of starting or stopping hard.
 * <p>
 * All sources of a channel share one start anchor (song position at a moment of the client's audio clock): a source
 * that starts later begins exactly where the others are, so walking around cannot shift them against each other.
 * The anchor comes from the server's game time once, when the first source of the channel starts.
 * <p>
 * Minecraft has only a few streaming channels (music and records need them too), so the number of sources is capped.
 */
public final class CreeperAudio {
    private static final int MAX_PER_CHANNEL = 4;
    private static final int MAX_TOTAL = 6;
    /** Sources this far beyond their range already get the song, so it is there when the player arrives. */
    private static final double PRELOAD_DISTANCE = 48;
    /** Full loudness up to this part of the range. */
    private static final double FULL_PART = 0.2;
    /** A source must be this much louder to replace a playing one (no flapping at the cap). */
    private static final float SWITCH_MARGIN = 0.05F;
    private static final int UPDATE_INTERVAL = 2;
    private static final long RETRY_MILLIS = 1000;

    private static final Map<Key, CreeperSoundInstance> PLAYING = new HashMap<>();
    private static final List<CreeperSoundInstance> ENDING = new ArrayList<>();
    private static final Map<Key, Long> FAILED = new HashMap<>();
    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
    /** Channel version for which "Now playing" was shown. */
    private static final Map<Integer, Integer> ANNOUNCED = new HashMap<>();
    private static int ticks;
    private static boolean dirty;
    /** Audio clock: real time without the time the game was paused (paused sounds do not advance). */
    private static long pausedNanos;
    private static long pauseStart = -1;

    private record Key(int code, BlockPos pos) {
    }

    /**
     * @param rank the gain plus a bonus for already playing sources, used only for choosing
     */
    private record Candidate(Key key, ChannelState state, ChannelState.Source source, SongCache.Decoded song,
                             float gain, float rank) {
    }

    /** Song position (in bytes of the decoded PCM) at a moment of the audio clock, for one channel version. */
    private record Anchor(int version, String songId, long bytes, long nanos) {
        long bytesAt(long now, int bytesPerSecond) {
            return bytes + (now - nanos) * bytesPerSecond / 1_000_000_000L;
        }
    }

    private CreeperAudio() {
    }

    static void channelChanged() {
        dirty = true;
    }

    private static long audioNanos() {
        return System.nanoTime() - pausedNanos;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            stopAll();
            return;
        }
        // Keep the audio clock still while the game (and with it every sound) is paused.
        if (minecraft.isPaused()) {
            if (pauseStart < 0) {
                pauseStart = System.nanoTime();
            }
            return;
        }
        if (pauseStart >= 0) {
            pausedNanos += System.nanoTime() - pauseStart;
            pauseStart = -1;
        }
        SoundManager sounds = minecraft.getSoundManager();
        ENDING.removeIf(sound -> !sounds.isActive(sound));
        if (!dirty && ++ticks % UPDATE_INTERVAL != 0) {
            return;
        }
        dirty = false;
        update(minecraft, level, sounds);
    }

    private static void update(Minecraft minecraft, ClientLevel level, SoundManager sounds) {
        Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
        long gameTime = level.getGameTime();

        List<Candidate> candidates = new ArrayList<>();
        Map<Integer, Float> channelPower = new HashMap<>();
        for (ChannelState state : CreeperClientState.channels().values()) {
            if (!state.playing()) {
                prefetch(level, state, listener);
                continue;
            }
            long position = state.positionTicks(gameTime);
            // The last second is left out, so a song is not started again just before its end.
            if (state.songTicks() > 0 && position >= state.songTicks() - 20) {
                continue;
            }
            List<Candidate> audible = new ArrayList<>();
            boolean near = false;
            SongCache.Decoded song = null;
            for (ChannelState.Source source : state.sources()) {
                double distance = SablePositions.center(level, source.pos()).distanceTo(listener);
                if (distance > source.range() + PRELOAD_DISTANCE) {
                    continue;
                }
                near = true;
                float gain = gain(distance, source.range()) * source.volume() / 100.0F;
                if (gain <= 0.0F) {
                    continue;
                }
                if (song == null) {
                    song = SongCache.decoded(state.songId());
                    if (song == null) {
                        break;
                    }
                }
                Key key = new Key(state.code(), source.pos());
                // Playing sources get a small bonus, so two similar ones do not keep swapping at the cap.
                float rank = PLAYING.containsKey(key) ? gain + SWITCH_MARGIN : gain;
                audible.add(new Candidate(key, state, source, song, gain, rank));
            }
            if (near && !SongCache.has(state.songId())) {
                SongCache.request(state.songId());
            }
            audible.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.rank).reversed());
            List<Candidate> chosen = audible.subList(0, Math.min(MAX_PER_CHANNEL, audible.size()));
            candidates.addAll(chosen);
            if (!chosen.isEmpty() && !Integer.valueOf(state.version()).equals(ANNOUNCED.get(state.code()))) {
                ANNOUNCED.put(state.code(), state.version());
                minecraft.gui.setNowPlaying(Component.literal(state.title()));
            }
        }
        candidates.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.rank).reversed());
        if (candidates.size() > MAX_TOTAL) {
            candidates = candidates.subList(0, MAX_TOTAL);
        }
        Map<Key, Candidate> wanted = new HashMap<>();
        for (Candidate candidate : candidates) {
            wanted.put(candidate.key, candidate);
            channelPower.merge(candidate.key.code(), candidate.gain * candidate.gain, Float::sum);
        }

        // Sources that are no longer wanted, or belong to an old playback, fade out.
        Iterator<Map.Entry<Key, CreeperSoundInstance>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, CreeperSoundInstance> entry = iterator.next();
            CreeperSoundInstance sound = entry.getValue();
            ChannelState state = CreeperClientState.channel(entry.getKey().code());
            boolean current = state != null && state.playing() && state.version() == sound.version
                    && state.songId().equals(sound.songId);
            if (!sounds.isActive(sound)) {
                iterator.remove();
            } else if (!current || !wanted.containsKey(entry.getKey())) {
                sound.fadeOut();
                ENDING.add(sound);
                iterator.remove();
            }
        }
        // Anchors of channels with nothing left playing are dropped: the next start syncs to game time again.
        ANCHORS.keySet().removeIf(code -> PLAYING.keySet().stream().noneMatch(key -> key.code() == code));

        long now = Util.getMillis();
        FAILED.values().removeIf(time -> now - time > RETRY_MILLIS);
        for (Candidate candidate : candidates) {
            float power = channelPower.getOrDefault(candidate.key.code(), 0.0F);
            // Balanced by total power: together never louder than one source at full volume.
            float volume = candidate.gain / (float) Math.max(1.0, Math.sqrt(power));
            CreeperSoundInstance playing = PLAYING.get(candidate.key);
            if (playing != null) {
                playing.setTarget(volume);
                continue;
            }
            if (FAILED.containsKey(candidate.key)) {
                continue;
            }
            CreeperSoundInstance sound = new CreeperSoundInstance(candidate.state, candidate.source, candidate.song,
                    volume, startBytes(candidate.state, candidate.song, gameTime));
            sounds.play(sound);
            if (sounds.isActive(sound)) {
                PLAYING.put(candidate.key, sound);
            } else {
                // No free streaming channel: try again a bit later.
                FAILED.put(candidate.key, now);
            }
        }
    }

    /**
     * A blaster nearby has a song chosen but does not play it yet: fetch and decode it already, so "Play" starts at
     * once instead of a few seconds into the song (the position keeps running while a song is transferred).
     */
    private static void prefetch(ClientLevel level, ChannelState state, Vec3 listener) {
        if (!state.active() || state.songId().isEmpty()) {
            return;
        }
        for (ChannelState.Source source : state.sources()) {
            if (SablePositions.center(level, source.pos()).distanceTo(listener) <= source.range() + PRELOAD_DISTANCE) {
                if (SongCache.has(state.songId())) {
                    // Starts decoding (and keeps it decoded while the player stays near).
                    SongCache.decoded(state.songId());
                } else {
                    SongCache.request(state.songId());
                }
                return;
            }
        }
    }

    /** Where a new source of this channel starts: where the others are, or (first source) from the game time. */
    private static long startBytes(ChannelState state, SongCache.Decoded song, long gameTime) {
        long nanos = audioNanos();
        Anchor anchor = ANCHORS.get(state.code());
        if (anchor == null || anchor.version != state.version() || !anchor.songId.equals(state.songId())) {
            long bytes = state.positionTicks(gameTime) * song.bytesPerSecond() / 20;
            anchor = new Anchor(state.version(), state.songId(), bytes, nanos);
            ANCHORS.put(state.code(), anchor);
        }
        return Math.max(0, anchor.bytesAt(nanos, song.bytesPerSecond())) & ~1L;
    }

    /** 1 up to a fifth of the range, then a smooth (cosine) fade to 0 at the range. */
    static float gain(double distance, int range) {
        double full = range * FULL_PART;
        if (distance <= full) {
            return 1.0F;
        }
        if (distance >= range) {
            return 0.0F;
        }
        double t = (distance - full) / (range - full);
        return (float) (0.5 * (1.0 + Math.cos(Math.PI * t)));
    }

    public static void stopAll() {
        if (PLAYING.isEmpty() && ENDING.isEmpty()) {
            return;
        }
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        PLAYING.values().forEach(sounds::stop);
        ENDING.forEach(sounds::stop);
        PLAYING.clear();
        ENDING.clear();
        FAILED.clear();
        ANCHORS.clear();
    }

    public static void reset() {
        stopAll();
        ANNOUNCED.clear();
    }
}
