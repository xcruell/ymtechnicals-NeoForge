package com.yesmenn.technicals.creeper.network;

import com.yesmenn.technicals.YMTechnicals;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** All packets of the Creeper system. */
public final class CreeperPayloads {
    /** Song data is sent in pieces of at most this size. */
    public static final int CHUNK_SIZE = 32 * 1024;
    /** Uploads go in smaller pieces: packets to the server may carry at most 32767 bytes. */
    public static final int UPLOAD_CHUNK_SIZE = 30 * 1024;

    private CreeperPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, name));
    }

    /** Server -> all players of a dimension: state of one channel. */
    public record Channel(ChannelState state) implements CustomPacketPayload {
        public static final Type<Channel> TYPE = payloadType("creeper_channel");
        public static final StreamCodec<FriendlyByteBuf, Channel> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.state.write(buf), buf -> new Channel(ChannelState.read(buf)));

        @Override
        public Type<Channel> type() {
            return TYPE;
        }
    }

    /** Client -> server: please send this song (it is not in the client cache). */
    public record SongRequest(String songId) implements CustomPacketPayload {
        public static final Type<SongRequest> TYPE = payloadType("creeper_song_request");
        public static final StreamCodec<ByteBuf, SongRequest> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(64).map(SongRequest::new, SongRequest::songId);

        @Override
        public Type<SongRequest> type() {
            return TYPE;
        }
    }

    /** Server -> client: one piece of a song file (Ogg Vorbis); the pieces come in order. */
    public record SongChunk(String songId, int total, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<SongChunk> TYPE = payloadType("creeper_song_chunk");
        public static final StreamCodec<FriendlyByteBuf, SongChunk> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.songId, 64);
                    buf.writeVarInt(payload.total);
                    buf.writeVarInt(payload.offset);
                    buf.writeByteArray(payload.data);
                },
                buf -> new SongChunk(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(),
                        buf.readByteArray(CHUNK_SIZE)));

        @Override
        public Type<SongChunk> type() {
            return TYPE;
        }
    }

    /** Client -> server: a button or setting of the CreeperBlaster3000 screen. */
    public record BlasterAction(BlockPos pos, Action action, int value, String text) implements CustomPacketPayload {
        public static final Type<BlasterAction> TYPE = payloadType("creeper_blaster_action");
        public static final StreamCodec<FriendlyByteBuf, BlasterAction> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeEnum(payload.action);
                    buf.writeVarInt(payload.value);
                    buf.writeUtf(payload.text, 2048);
                },
                buf -> new BlasterAction(buf.readBlockPos(), buf.readEnum(Action.class), buf.readVarInt(),
                        buf.readUtf(2048)));

        public enum Action {
            /** Screen opened: send the library. */
            OPEN,
            CLOSE,
            PLAY,
            PAUSE,
            STOP,
            /** value = position in seconds */
            SEEK,
            /** text = song ID */
            SELECT,
            /** text = song ID */
            DELETE,
            /** text = song ID: download again from its link (current quality) */
            REDOWNLOAD,
            /** text = link */
            DOWNLOAD,
            /** text = link (kept in the field) */
            LINK,
            /** value = code */
            CODE,
            /** value = blocks */
            RANGE,
            /** value = percent */
            VOLUME,
            /** value = 0/1 */
            LOOP
        }

        public BlasterAction(BlockPos pos, Action action) {
            this(pos, action, 0, "");
        }

        @Override
        public Type<BlasterAction> type() {
            return TYPE;
        }
    }

    /** Client -> server: settings of a CreeperSpeaker3000. */
    public record SpeakerSettings(BlockPos pos, int code, int range, int volume) implements CustomPacketPayload {
        public static final Type<SpeakerSettings> TYPE = payloadType("creeper_speaker_settings");
        public static final StreamCodec<ByteBuf, SpeakerSettings> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SpeakerSettings::pos,
                ByteBufCodecs.VAR_INT, SpeakerSettings::code,
                ByteBufCodecs.VAR_INT, SpeakerSettings::range,
                ByteBufCodecs.VAR_INT, SpeakerSettings::volume,
                SpeakerSettings::new);

        @Override
        public Type<SpeakerSettings> type() {
            return TYPE;
        }
    }

    /**
     * Server -> players with an open blaster screen: the song library and the player's download rights and limits.
     *
     * @param quotaBytes      -1 without a limit
     * @param dailyLimitBytes -1 without a limit
     * @param maxUploadBytes  largest local file the player may upload, -1 when uploads are not possible
     */
    public record Library(List<SongInfo> songs, boolean mayDownload, long usedBytes, long quotaBytes, long dailyBytes,
                          long dailyLimitBytes, long maxUploadBytes) implements CustomPacketPayload {
        public static final Type<Library> TYPE = payloadType("creeper_library");
        public static final StreamCodec<FriendlyByteBuf, Library> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.songs.size());
                    payload.songs.forEach(song -> song.write(buf));
                    buf.writeBoolean(payload.mayDownload);
                    buf.writeVarLong(payload.usedBytes);
                    buf.writeLong(payload.quotaBytes);
                    buf.writeVarLong(payload.dailyBytes);
                    buf.writeLong(payload.dailyLimitBytes);
                    buf.writeLong(payload.maxUploadBytes);
                },
                buf -> {
                    int count = Math.min(buf.readVarInt(), 100_000);
                    List<SongInfo> songs = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        songs.add(SongInfo.read(buf));
                    }
                    return new Library(List.copyOf(songs), buf.readBoolean(), buf.readVarLong(), buf.readLong(),
                            buf.readVarLong(), buf.readLong(), buf.readLong());
                });

        @Override
        public Type<Library> type() {
            return TYPE;
        }
    }

    /**
     * Server -> player: status line of the blaster screen (download progress, errors).
     *
     * @param progress 0..100, or -1 without a progress bar
     * @param busy     a download of this player is running
     */
    public record BlasterStatus(BlockPos pos, Component text, int progress, boolean busy)
            implements CustomPacketPayload {
        public static final Type<BlasterStatus> TYPE = payloadType("creeper_blaster_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, BlasterStatus> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BlasterStatus::pos,
                ComponentSerialization.TRUSTED_STREAM_CODEC, BlasterStatus::text,
                ByteBufCodecs.VAR_INT, BlasterStatus::progress,
                ByteBufCodecs.BOOL, BlasterStatus::busy,
                BlasterStatus::new);

        @Override
        public Type<BlasterStatus> type() {
            return TYPE;
        }
    }

    /** Client -> server: a local audio file follows for the blaster at {@code pos}. */
    public record UploadStart(BlockPos pos, String fileName, long size) implements CustomPacketPayload {
        public static final Type<UploadStart> TYPE = payloadType("creeper_upload_start");
        public static final StreamCodec<FriendlyByteBuf, UploadStart> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.fileName, 256);
                    buf.writeVarLong(payload.size);
                },
                buf -> new UploadStart(buf.readBlockPos(), buf.readUtf(256), buf.readVarLong()));

        @Override
        public Type<UploadStart> type() {
            return TYPE;
        }
    }

    /** Client -> server: one piece of the upload, in order; an empty piece ends it. */
    public record UploadChunk(long offset, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadChunk> TYPE = payloadType("creeper_upload_chunk");
        public static final StreamCodec<FriendlyByteBuf, UploadChunk> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarLong(payload.offset);
                    buf.writeByteArray(payload.data);
                },
                buf -> new UploadChunk(buf.readVarLong(), buf.readByteArray(UPLOAD_CHUNK_SIZE)));

        @Override
        public Type<UploadChunk> type() {
            return TYPE;
        }
    }

    /**
     * Server -> client: bytes of the upload received so far (the client keeps only a little ahead of it), or -1 when
     * the server stopped the upload.
     */
    public record UploadAck(long received) implements CustomPacketPayload {
        public static final Type<UploadAck> TYPE = payloadType("creeper_upload_ack");
        public static final StreamCodec<ByteBuf, UploadAck> STREAM_CODEC =
                ByteBufCodecs.VAR_LONG.map(UploadAck::new, UploadAck::received);

        @Override
        public Type<UploadAck> type() {
            return TYPE;
        }
    }
}
