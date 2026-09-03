package com.yesmenn.technicals.network;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenSoundeffectz3000ScreenPayload(
        BlockPos pos,
        String soundId,
        int volume,
        int pitch,
        int range,
        boolean looping,
        boolean redstoneEnabled,
        boolean playing) implements CustomPacketPayload {
    public static final Type<OpenSoundeffectz3000ScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, "open_soundeffectz_3000_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSoundeffectz3000ScreenPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenSoundeffectz3000ScreenPayload::encode, OpenSoundeffectz3000ScreenPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(soundId, 256);
        buffer.writeVarInt(volume);
        buffer.writeVarInt(pitch);
        buffer.writeVarInt(range);
        buffer.writeBoolean(looping);
        buffer.writeBoolean(redstoneEnabled);
        buffer.writeBoolean(playing);
    }

    private static OpenSoundeffectz3000ScreenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenSoundeffectz3000ScreenPayload(
                buffer.readBlockPos(),
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
