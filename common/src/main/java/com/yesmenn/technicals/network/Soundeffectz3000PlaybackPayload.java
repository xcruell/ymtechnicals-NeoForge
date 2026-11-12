package com.yesmenn.technicals.network;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Soundeffectz3000PlaybackPayload(
        BlockPos pos,
        String soundId,
        int volume,
        int pitch,
        int range,
        boolean looping,
        int sequence) implements CustomPacketPayload {
    public static final Type<Soundeffectz3000PlaybackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, "soundeffectz_3000_playback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Soundeffectz3000PlaybackPayload> STREAM_CODEC =
            StreamCodec.ofMember(Soundeffectz3000PlaybackPayload::encode, Soundeffectz3000PlaybackPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(soundId, 256);
        buffer.writeVarInt(volume);
        buffer.writeVarInt(pitch);
        buffer.writeVarInt(range);
        buffer.writeBoolean(looping);
        buffer.writeVarInt(sequence);
    }

    private static Soundeffectz3000PlaybackPayload decode(RegistryFriendlyByteBuf buffer) {
        return new Soundeffectz3000PlaybackPayload(
                buffer.readBlockPos(),
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
