package com.yesmenn.technicals.creeper.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A song of the server library.
 *
 * @param id      SHA-256 of the Ogg file (also its file name and the client cache key)
 * @param artist  channel / uploader of the source, may be empty
 * @param addedBy   name of the player who downloaded it
 * @param bytes     file size
 * @param deletable      the receiving player may delete it
 * @param redownloadable the receiving player may download it again from its link (current quality)
 */
public record SongInfo(String id, String title, String artist, String addedBy, int seconds, long bytes,
                       boolean deletable, boolean redownloadable) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeUtf(title, 256);
        buf.writeUtf(artist, 128);
        buf.writeUtf(addedBy, 64);
        buf.writeVarInt(seconds);
        buf.writeVarLong(bytes);
        buf.writeBoolean(deletable);
        buf.writeBoolean(redownloadable);
    }

    public static SongInfo read(FriendlyByteBuf buf) {
        return new SongInfo(buf.readUtf(64), buf.readUtf(256), buf.readUtf(128), buf.readUtf(64), buf.readVarInt(),
                buf.readVarLong(), buf.readBoolean(), buf.readBoolean());
    }
}
