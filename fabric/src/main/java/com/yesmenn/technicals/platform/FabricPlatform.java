package com.yesmenn.technicals.platform;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.creeper.CreeperPermission;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class FabricPlatform implements Platform.Impl {
    /** fabric-permissions-api (optional): Permissions.check(Entity, String, boolean), null without it. */
    @Nullable
    private static final MethodHandle PERMISSION_CHECK = findPermissionCheck();

    @Nullable
    private static MethodHandle findPermissionCheck() {
        try {
            Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return MethodHandles.publicLookup().findStatic(permissions, "check",
                    MethodType.methodType(boolean.class, Entity.class, String.class, boolean.class));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean hasPermission(ServerPlayer player, CreeperPermission permission) {
        if (PERMISSION_CHECK != null) {
            try {
                return (boolean) PERMISSION_CHECK.invoke((Entity) player, permission.node(),
                        permission.byDefault(player));
            } catch (Throwable ignored) {
                // falls back to the permission level
            }
        }
        return permission.byDefault(player);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        T value = Registry.register(registry, ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, name),
                factory.get());
        return () -> value;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
