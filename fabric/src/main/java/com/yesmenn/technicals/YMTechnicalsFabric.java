package com.yesmenn.technicals;

import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import com.yesmenn.technicals.creeper.server.CreeperServer;
import com.yesmenn.technicals.platform.FabricPlatform;
import com.yesmenn.technicals.platform.Platform;
import com.yesmenn.technicals.registry.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric entrypoint: loader services, registries, packets and the server events of the common code. */
public final class YMTechnicalsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.init(new FabricPlatform());
        YMTechnicals.init();

        ModNetworking.toClient().forEach(YMTechnicalsFabric::registerToClient);
        ModNetworking.toServer().forEach(YMTechnicalsFabric::registerToServer);

        ServerLifecycleEvents.SERVER_STARTING.register(CreeperServer::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CreeperServer.serverStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CreeperServer.serverStopped());
        ServerTickEvents.END_WORLD_TICK.register(CreeperServer::levelTicked);
        ServerTickEvents.END_SERVER_TICK.register(CreeperServer::serverTicked);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CreeperServer.playerEnteredLevel(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CreeperServer.playerLeft(handler.player.getUUID()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                CreeperServer.playerEnteredLevel(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CreeperServer.playerEnteredLevel(newPlayer));

        // NeoForge calls onLoad itself; on Fabric the load event does it.
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof CreeperBlaster3000BlockEntity blaster) {
                blaster.onLoad();
            } else if (blockEntity instanceof CreeperSpeaker3000BlockEntity speaker) {
                speaker.onLoad();
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerToClient(ModNetworking.ToClient<T> entry) {
        PayloadTypeRegistry.playS2C().register(entry.type(), entry.codec());
    }

    private static <T extends CustomPacketPayload> void registerToServer(ModNetworking.ToServer<T> entry) {
        PayloadTypeRegistry.playC2S().register(entry.type(), entry.codec());
        ServerPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> entry.handler().accept(payload, context.player()));
    }
}
