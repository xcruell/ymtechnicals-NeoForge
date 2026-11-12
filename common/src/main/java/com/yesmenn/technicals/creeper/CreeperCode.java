package com.yesmenn.technicals.creeper;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Pairing code of the Creeper system: three slots with one of eight colors each. A CreeperSpeaker3000 plays what the
 * CreeperBlaster3000 with the same code plays. Stored as one int (slot 0 in the highest three bits), 0..511.
 */
public final class CreeperCode {
    public static final int COLORS = 8;
    public static final int SLOTS = 3;
    public static final int COUNT = COLORS * COLORS * COLORS;
    /** Red, green, blue: the code of new speakers and of the first blaster. */
    public static final int DEFAULT = of(0, 1, 2);

    private static final int[] RGB = {0xE53935, 0x43A047, 0x1E88E5, 0xFDD835, 0x8E24AA, 0xFB8C00, 0x00ACC1, 0xFFFFFF};
    private static final String[] NAMES = {"red", "green", "blue", "yellow", "purple", "orange", "cyan", "white"};

    private CreeperCode() {
    }

    public static int of(int first, int second, int third) {
        return Math.floorMod(first, COLORS) << 6 | Math.floorMod(second, COLORS) << 3 | Math.floorMod(third, COLORS);
    }

    public static int clamp(int code) {
        return Math.floorMod(code, COUNT);
    }

    /** Color (0..7) of one slot (0..2). */
    public static int slot(int code, int slot) {
        return code >> 3 * (SLOTS - 1 - slot) & 7;
    }

    public static int withSlot(int code, int slot, int color) {
        int shift = 3 * (SLOTS - 1 - slot);
        return code & ~(7 << shift) | Math.floorMod(color, COLORS) << shift;
    }

    public static int rgb(int color) {
        return RGB[Math.floorMod(color, COLORS)];
    }

    public static Component colorName(int color) {
        return Component.translatable("gui.ymtechnicals.creeper.color." + NAMES[Math.floorMod(color, COLORS)]);
    }

    /** Three colored squares, e.g. for tooltips and chat. */
    public static MutableComponent squares(int code) {
        MutableComponent result = Component.empty();
        for (int slot = 0; slot < SLOTS; slot++) {
            result.append(Component.literal("■").withColor(rgb(slot(code, slot))));
        }
        return result;
    }

    /** "Red, Green, Blue" */
    public static MutableComponent names(int code) {
        MutableComponent result = Component.empty();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (slot > 0) {
                result.append(", ");
            }
            result.append(colorName(slot(code, slot)));
        }
        return result;
    }
}
