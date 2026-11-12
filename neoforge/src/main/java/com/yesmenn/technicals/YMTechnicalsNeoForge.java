package com.yesmenn.technicals;

import com.yesmenn.technicals.creeper.server.CreeperServer;
import com.yesmenn.technicals.platform.NeoForgePlatform;
import com.yesmenn.technicals.platform.Platform;
import com.yesmenn.technicals.registry.ModNetworking;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/** NeoForge entrypoint: loader services, registries, packets and the server events of the common code. */
@Mod(YMTechnicals.MODID)
public final class YMTechnicalsNeoForge {
    public YMTechnicalsNeoForge(IEventBus modBus) {
        NeoForgePlatform platform = new NeoForgePlatform();
        Platform.init(platform);
        YMTechnicals.init();
        platform.registerAll(modBus);
        modBus.addListener(YMTechnicalsNeoForge::registerPayloads);
        modBus.addListener(EventPriority.LOWEST, YMTechnicalsNeoForge::aliasYesMennIds);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(platform.permissionNodes()));
        game.addListener((ServerAboutToStartEvent event) -> CreeperServer.serverStarting(event.getServer()));
        game.addListener((ServerStoppingEvent event) -> CreeperServer.serverStopping());
        game.addListener((ServerStoppedEvent event) -> CreeperServer.serverStopped());
        game.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                CreeperServer.levelTicked(level);
            }
        });
        game.addListener((ServerTickEvent.Post event) -> CreeperServer.serverTicked(event.getServer()));
        game.addListener((PlayerEvent.PlayerLoggedInEvent event) -> enteredLevel(event));
        game.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> enteredLevel(event));
        game.addListener((PlayerEvent.PlayerRespawnEvent event) -> enteredLevel(event));
        game.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                CreeperServer.playerLeft(event.getEntity().getUUID()));
    }

    private static void enteredLevel(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreeperServer.playerEnteredLevel(player);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetworking.PROTOCOL_VERSION);
        ModNetworking.toClient().forEach(entry -> toClient(registrar, entry));
        ModNetworking.toServer().forEach(entry -> toServer(registrar, entry));
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void toClient(
            PayloadRegistrar registrar, ModNetworking.ToClient<T> entry) {
        registrar.playToClient(entry.type(), entry.codec(), (payload, context) -> entry.handler().accept(payload));
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void toServer(
            PayloadRegistrar registrar, ModNetworking.ToServer<T> entry) {
        registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                entry.handler().accept(payload, player);
            }
        });
    }

    /**
     * The Creeper blocks and the code sheet came from the YesMenn mod (NeoForge only); worlds from back then keep them
     * when that mod is not installed any more.
     */
    private static void aliasYesMennIds(RegisterEvent event) {
        if (ModList.get().isLoaded("yesmenn")) {
            return;
        }
        Registry<?> registry = event.getRegistry();
        for (String name : YMTechnicals.FROM_YESMENN) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, name);
            if (registry.containsKey(id)) {
                registry.addAlias(ResourceLocation.fromNamespaceAndPath("yesmenn", name), id);
            }
        }
    }
}
