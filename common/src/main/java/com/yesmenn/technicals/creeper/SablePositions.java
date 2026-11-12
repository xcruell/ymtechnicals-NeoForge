package com.yesmenn.technicals.creeper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Where a block really is when Sable (optional) has moved it onto a sub-level (e.g. a flying ship): the block center
 * transformed by the sub-level's pose. Without Sable, or for normal blocks, the plain block center.
 */
public final class SablePositions {
    @Nullable
    private static final Handles HANDLES = Handles.find();

    private SablePositions() {
    }

    public static Vec3 center(Level level, BlockPos pos) {
        Vec3 center = pos.getCenter();
        if (HANDLES == null) {
            return center;
        }
        try {
            Object container = HANDLES.container.invoke(level);
            Object plot = container == null ? null : HANDLES.plot.invoke(container, new ChunkPos(pos));
            if (plot == null || !(boolean) HANDLES.contains.invoke(plot, center)) {
                return center;
            }
            Object subLevel = HANDLES.subLevel.invoke(plot);
            Object pose = subLevel == null ? null : HANDLES.pose.invoke(subLevel);
            Object moved = pose == null ? null : HANDLES.transform.invoke(pose, center);
            return moved instanceof Vec3 vec ? vec : center;
        } catch (Throwable ignored) {
            return center;
        }
    }

    private record Handles(MethodHandle container, MethodHandle plot, MethodHandle contains, MethodHandle subLevel,
                           MethodHandle pose, MethodHandle transform) {
        @Nullable
        static Handles find() {
            try {
                Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Class<?> plotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                // Exact descriptors only: enumerating Sable's methods would load client classes on a server.
                return new Handles(
                        lookup.findStatic(containerClass, "getContainer", MethodType.methodType(containerClass, Level.class)),
                        lookup.findVirtual(containerClass, "getPlot", MethodType.methodType(plotClass, ChunkPos.class)),
                        lookup.findVirtual(plotClass, "contains", MethodType.methodType(boolean.class, Vec3.class)),
                        lookup.findVirtual(plotClass, "getSubLevel", MethodType.methodType(subLevelClass)),
                        lookup.findVirtual(subLevelClass, "logicalPose", MethodType.methodType(poseClass)),
                        lookup.findVirtual(poseClass, "transformPosition", MethodType.methodType(Vec3.class, Vec3.class)));
            } catch (Throwable ignored) {
                // Sable is optional; without it blocks stay where they are.
                return null;
            }
        }
    }
}
