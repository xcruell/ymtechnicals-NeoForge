package com.yesmenn.technicals.platform;

import com.yesmenn.technicals.creeper.CreeperPermission;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Loader services used by the common code. Each loader sets its implementation first thing in its entrypoint, before
 * any other mod class is touched.
 */
public final class Platform {
    private static volatile Impl instance;

    private Platform() {
    }

    public static void init(Impl impl) {
        instance = impl;
    }

    public static Path configDir() {
        return instance.configDir();
    }

    /** Game folder (client) or server folder (dedicated server). */
    public static Path gameDir() {
        return instance.gameDir();
    }

    /** Asks the permission system (permission mods) about a node; falls back to the node's permission level. */
    public static boolean hasPermission(ServerPlayer player, CreeperPermission permission) {
        return instance.hasPermission(player, permission);
    }

    public static boolean isModLoaded(String modId) {
        return instance.isModLoaded(modId);
    }

    public static boolean isClient() {
        return instance.isClient();
    }

    /**
     * Registers an object. NeoForge defers it to the registry event; Fabric registers it at once, so register
     * dependencies (blocks) before the objects using them (items, block entity types).
     */
    public static <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        return instance.register(registry, name, factory);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        instance.sendToPlayer(player, payload);
    }

    public static void sendToLevel(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            instance.sendToPlayer(player, payload);
        }
    }

    public interface Impl {
        Path configDir();

        Path gameDir();

        boolean hasPermission(ServerPlayer player, CreeperPermission permission);

        boolean isModLoaded(String modId);

        boolean isClient();

        <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory);

        void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
    }
}
