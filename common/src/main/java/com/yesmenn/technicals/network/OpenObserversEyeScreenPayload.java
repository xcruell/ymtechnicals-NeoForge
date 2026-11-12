package com.yesmenn.technicals.network;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenObserversEyeScreenPayload(
        BlockPos pos,
        int sizeX,
        int sizeY,
        int sizeZ,
        int offsetX,
        int offsetY,
        int offsetZ,
        int pattern,
        int outputMode,
        int filterMode,
        boolean preview,
        String players) implements CustomPacketPayload {
    public static final Type<OpenObserversEyeScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, "open_observers_eye_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenObserversEyeScreenPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenObserversEyeScreenPayload::encode, OpenObserversEyeScreenPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(sizeX);
        buffer.writeVarInt(sizeY);
        buffer.writeVarInt(sizeZ);
        buffer.writeVarInt(offsetX);
        buffer.writeVarInt(offsetY);
        buffer.writeVarInt(offsetZ);
        buffer.writeVarInt(pattern);
        buffer.writeVarInt(outputMode);
        buffer.writeVarInt(filterMode);
        buffer.writeBoolean(preview);
        buffer.writeUtf(players, 512);
    }

    private static OpenObserversEyeScreenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenObserversEyeScreenPayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(512));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
