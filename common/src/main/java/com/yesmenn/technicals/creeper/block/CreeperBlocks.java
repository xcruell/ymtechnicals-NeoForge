package com.yesmenn.technicals.creeper.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Consumer;

/** Shared bits of the Creeper blocks. */
public final class CreeperBlocks {
    private static final ResourceLocation CREATE_WRENCH = ResourceLocation.fromNamespaceAndPath("create", "wrench");

    /** Opens the screens on the client; set by the client setup (the blocks must not touch client classes). */
    public static Consumer<BlockPos> openBlasterScreen = pos -> {
    };
    public static Consumer<BlockPos> openSpeakerScreen = pos -> {
    };

    private CreeperBlocks() {
    }

    /** Create's wrench turns the blocks (Create is optional, so it is compared by ID). */
    static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(CREATE_WRENCH);
    }

    /** Boxes in pixels (x1, y1, z1, x2, y2, z2, ...) for the block facing north. */
    static VoxelShape boxes(double... pixels) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i + 5 < pixels.length; i += 6) {
            shape = Shapes.or(shape, Shapes.box(pixels[i] / 16, pixels[i + 1] / 16, pixels[i + 2] / 16,
                    pixels[i + 3] / 16, pixels[i + 4] / 16, pixels[i + 5] / 16));
        }
        return shape.optimize();
    }

    /** The shape turned clockwise around the block center by {@code turns} quarter turns (north -> east -> ...). */
    static VoxelShape turn(VoxelShape shape, int turns) {
        VoxelShape result = shape;
        for (int turn = 0; turn < turns; turn++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : result.toAabbs()) {
                next = Shapes.or(next, Shapes.box(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX));
            }
            result = next;
        }
        return result.optimize();
    }
}
