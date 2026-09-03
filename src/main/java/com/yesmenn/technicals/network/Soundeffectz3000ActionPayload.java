package com.yesmenn.technicals.network;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Soundeffectz3000ActionPayload(
        BlockPos pos,
        int action,
        String soundId,
        int volume,
        int pitch,
        int range,
        boolean looping,
        boolean redstoneEnabled) implements CustomPacketPayload {
    public static final int SAVE = 0;
    public static final int PLAY = 1;
    public static final int STOP = 2;
    public static final Type<Soundeffectz3000ActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, "soundeffectz_3000_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Soundeffectz3000ActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(Soundeffectz3000ActionPayload::encode, Soundeffectz3000ActionPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(action);
        buffer.writeUtf(soundId, 256);
        buffer.writeVarInt(volume);
        buffer.writeVarInt(pitch);
        buffer.writeVarInt(range);
        buffer.writeBoolean(looping);
        buffer.writeBoolean(redstoneEnabled);
    }

    private static Soundeffectz3000ActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new Soundeffectz3000ActionPayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
