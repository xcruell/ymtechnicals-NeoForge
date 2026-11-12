package com.yesmenn.technicals.creeper.client;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.creeper.SablePositions;
import com.yesmenn.technicals.creeper.network.ChannelState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTSourceLatency;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One playing Creeper source (the blaster or a speaker): a normal positional Minecraft sound in the "Jukebox/Note
 * Blocks" category, so volume settings, pause, subtitles and distance falloff work as for any record. Its audio is
 * streamed from the song's shared decoded PCM, starting at the channel's current play position.
 */
public final class CreeperSoundInstance extends AbstractTickableSoundInstance {
    /** Registered in sounds.json for the subtitle; the actual audio comes from {@link #getStream}. */
    static final ResourceLocation EVENT = ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID,
            "creeper_blaster_3000.music");
    private static final ByteBuffer EMPTY = ByteBuffer.allocateDirect(0);

    /** Volume change per tick: fades in within about half a second, out a bit faster. */
    private static final float FADE_IN = 0.1F;
    private static final float FADE_OUT = 0.15F;

    final int code;
    final BlockPos pos;
    final int version;
    final String songId;
    private final SongCache.Decoded song;
    private final int range;
    /** Start position in the decoded PCM (bytes), shared with the channel's other sources (CreeperAudio). */
    private final long startBytes;
    private float target;
    private boolean ending;

    CreeperSoundInstance(ChannelState state, ChannelState.Source source, SongCache.Decoded song, float volume,
                         long startBytes) {
        super(SoundEvent.createVariableRangeEvent(EVENT), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.code = state.code();
        this.pos = source.pos();
        this.version = state.version();
        this.songId = state.songId();
        this.song = song;
        this.range = Math.max(1, source.range());
        this.startBytes = startBytes;
        // Starts silent and fades in to the target volume.
        this.volume = 0.0F;
        this.target = volume;
        this.looping = false;
        this.delay = 0;
        // Distance falloff comes from CreeperAudio (a smoother curve than OpenAL's linear one); OpenAL only pans.
        this.attenuation = Attenuation.NONE;
        this.relative = false;
        moveTo(pos.getCenter());
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents events = super.resolve(manager);
        // Streamed, and fading out over the source's own range.
        this.sound = new Sound(EVENT, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true,
                false, range);
        return events;
    }

    /**
     * NeoForge asks the sound instance for its stream (this overrides its sound instance extension); on Fabric a mixin
     * in the sound engine calls {@link #openStream()} instead.
     */
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        return openStream();
    }

    /** The song from the start position CreeperAudio chose. */
    public CompletableFuture<AudioStream> openStream() {
        return CompletableFuture.completedFuture(new PcmStream(new Group(code, version, songId), song,
                (int) Math.min(startBytes, song.pcm().limit())));
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            stop();
            return;
        }
        moveTo(SablePositions.center(minecraft.level, pos));
        if (volume < target) {
            volume = Math.min(target, volume + FADE_IN);
        } else if (volume > target) {
            volume = Math.max(target, volume - FADE_OUT);
        }
        if (ending && volume <= 0.0F) {
            stop();
        }
    }

    void setTarget(float target) {
        if (!ending) {
            this.target = target;
        }
    }

    /** Fades out and then stops. */
    void fadeOut() {
        ending = true;
        target = 0.0F;
    }

    boolean isEnding() {
        return ending;
    }

    private void moveTo(Vec3 position) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }

    /** The sources of one playback of one channel. */
    private record Group(int code, int version, String songId) {
    }

    /**
     * Reads slices of the shared PCM; no copies, no decoding.
     * <p>
     * When the sound engine attaches it to its OpenAL source ({@link #attach}, sound thread, right before the source
     * starts), it moves to where an already playing source of the same playback really is, read from OpenAL. That
     * keeps sources in step on the sound card's own clock: the start position from the system clock alone drifts a
     * little against it while a channel plays for minutes.
     */
    public static final class PcmStream implements AudioStream {
        /** Attached streams per playback; only touched on the sound thread, but read from the render thread too. */
        private static final Map<Group, Set<PcmStream>> GROUPS = new ConcurrentHashMap<>();
        /** Bytes of the last pieces handed to OpenAL (still queued ones are among them). */
        private static final int REMEMBERED_PIECES = 16;

        private final Group group;
        private final ByteBuffer pcm;
        private final AudioFormat format;
        private final ArrayDeque<Integer> pieces = new ArrayDeque<>();
        private int position;
        private int source = -1;

        PcmStream(Group group, SongCache.Decoded song, int start) {
            this.group = group;
            this.pcm = song.pcm();
            this.format = new AudioFormat(song.sampleRate(), 16, 1, true, false);
            this.position = start;
        }

        /** Called (through a mixin) when the stream is attached to OpenAL source {@code source}, before any read. */
        public void attach(int source) {
            this.source = source;
            Set<PcmStream> siblings = GROUPS.computeIfAbsent(group, key -> ConcurrentHashMap.newKeySet());
            for (PcmStream sibling : siblings) {
                long played = sibling.played();
                if (played >= 0) {
                    position = (int) Math.min(played & ~1L, pcm.limit());
                    break;
                }
            }
            siblings.add(this);
        }

        /** Position the source is playing right now, or -1 when it is not playing. Sound thread. */
        private long played() {
            if (source < 0 || AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                return -1;
            }
            int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            long offset;
            if (AL.getCapabilities().AL_SOFT_source_latency) {
                // Exact to the sample (the plain offset only moves in steps of the mixer, about 10 ms).
                long[] values = new long[2];
                SOFTSourceLatency.alGetSourcei64vSOFT(source, SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT, values);
                offset = (values[0] >> 32) * 2;
            } else {
                offset = AL10.alGetSourcei(source, AL11.AL_BYTE_OFFSET);
            }
            if (queued > pieces.size() || AL10.alGetError() != AL10.AL_NO_ERROR) {
                return -1;
            }
            long queuedBytes = 0;
            Iterator<Integer> newest = pieces.descendingIterator();
            for (int i = 0; i < queued; i++) {
                queuedBytes += newest.next();
            }
            return position - queuedBytes + offset;
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public ByteBuffer read(int size) {
            int length = Math.min(size & ~1, pcm.limit() - position);
            if (length <= 0) {
                return EMPTY;
            }
            ByteBuffer slice = pcm.slice(position, length).order(ByteOrder.LITTLE_ENDIAN);
            position += length;
            pieces.addLast(length);
            if (pieces.size() > REMEMBERED_PIECES) {
                pieces.removeFirst();
            }
            return slice;
        }

        @Override
        public void close() {
            Set<PcmStream> siblings = GROUPS.get(group);
            if (siblings != null) {
                siblings.remove(this);
                if (siblings.isEmpty()) {
                    GROUPS.remove(group, siblings);
                }
            }
        }
    }
}
