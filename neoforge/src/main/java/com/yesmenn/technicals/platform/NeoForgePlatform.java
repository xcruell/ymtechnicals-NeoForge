package com.yesmenn.technicals.platform;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.creeper.CreeperPermission;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class NeoForgePlatform implements Platform.Impl {
    private final Map<ResourceKey<? extends Registry<?>>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    /** Permission nodes; without a permission mod they fall back to the permission level of each node. */
    private final Map<CreeperPermission, PermissionNode<Boolean>> nodes = new EnumMap<>(CreeperPermission.class);

    public NeoForgePlatform() {
        for (CreeperPermission permission : CreeperPermission.values()) {
            nodes.put(permission, new PermissionNode<>(YMTechnicals.MODID, permission.nodeName(),
                    PermissionTypes.BOOLEAN, (player, uuid, context) ->
                    player != null && permission.byDefault(player)));
        }
    }

    /** For {@code PermissionGatherEvent.Nodes}. */
    public Collection<PermissionNode<?>> permissionNodes() {
        return List.copyOf(nodes.values());
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean hasPermission(ServerPlayer player, CreeperPermission permission) {
        return PermissionAPI.getPermission(player, nodes.get(permission));
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        DeferredRegister<R> register = (DeferredRegister<R>) registers.computeIfAbsent(registry.key(),
                key -> DeferredRegister.create((ResourceKey<? extends Registry<R>>) key, YMTechnicals.MODID));
        return register.register(name, factory);
    }

    /** Hooks every register filled through {@link #register} to the mod bus; call after the common init. */
    public void registerAll(IEventBus modBus) {
        registers.values().forEach(register -> register.register(modBus));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
