package com.yesmenn.technicals.network;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Soundeffectz3000StopPayload(BlockPos pos, int sequence) implements CustomPacketPayload {
    public static final Type<Soundeffectz3000StopPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, "soundeffectz_3000_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Soundeffectz3000StopPayload> STREAM_CODEC =
            StreamCodec.ofMember(Soundeffectz3000StopPayload::encode, Soundeffectz3000StopPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(sequence);
    }

    private static Soundeffectz3000StopPayload decode(RegistryFriendlyByteBuf buffer) {
        return new Soundeffectz3000StopPayload(buffer.readBlockPos(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
