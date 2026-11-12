package com.yesmenn.technicals.creeper.client;

import com.yesmenn.technicals.creeper.network.CreeperPayloads;

/** Client handlers of the Creeper packets (run on the client thread). */
public final class CreeperClientPackets {
    private CreeperClientPackets() {
    }

    public static void channel(CreeperPayloads.Channel payload) {
        CreeperClientState.putChannel(payload.state());
        CreeperAudio.channelChanged();
    }

    public static void songChunk(CreeperPayloads.SongChunk payload) {
        SongCache.receive(payload);
    }

    public static void library(CreeperPayloads.Library payload) {
        CreeperClientState.setLibrary(payload);
    }

    public static void uploadAck(CreeperPayloads.UploadAck payload) {
        CreeperUploads.ack(payload.received());
    }

    public static void status(CreeperPayloads.BlasterStatus payload) {
        CreeperClientState.setStatus(new CreeperClientState.Status(payload.pos(), payload.text(), payload.progress(),
                payload.busy()));
    }
}
