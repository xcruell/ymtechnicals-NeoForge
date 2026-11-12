package com.yesmenn.technicals.creeper.client.screen;

import com.yesmenn.ducttape.client.kit.YmElement;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.element.YmActionMenu;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.technicals.creeper.CreeperCode;
import com.yesmenn.technicals.creeper.client.CreeperClientState;
import com.yesmenn.technicals.creeper.network.SongInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** The custom YM kit elements of the Creeper screens. */
final class CreeperWidgets {
    private CreeperWidgets() {
    }

    static String time(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    /** The three code colors: left click next color, right click previous, mouse wheel too. */
    static final class CodeSlots extends YmElement {
        private static final int GAP = 4;
        private final IntSupplier code;
        private final IntConsumer onChange;

        CodeSlots(IntSupplier code, IntConsumer onChange) {
            super(3 * 18 + 2 * GAP, 16);
            this.code = code;
            this.onChange = onChange;
            setTooltip(() -> List.of(CreeperCode.names(code.getAsInt()),
                    Component.translatable("gui.ymtechnicals.creeper.code.hint")));
        }

        private int slotWidth() {
            return Math.min(18, (width - 2 * GAP) / 3);
        }

        private int slotAt(double mouseX) {
            int slot = (int) ((mouseX - getX()) / (slotWidth() + GAP));
            return slot >= 0 && slot < 3 && mouseX - getX() - slot * (slotWidth() + GAP) < slotWidth() ? slot : -1;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YmStyle style = style();
            int value = code.getAsInt();
            int slotWidth = slotWidth();
            for (int slot = 0; slot < 3; slot++) {
                int x = getX() + slot * (slotWidth + GAP);
                boolean hovered = isMouseOver(mouseX, mouseY) && slotAt(mouseX) == slot;
                style.drawInset(graphics, x, getY(), slotWidth, height);
                graphics.fill(x + 2, getY() + 2, x + slotWidth - 2, getY() + height - 2,
                        0xFF000000 | CreeperCode.rgb(CreeperCode.slot(value, slot)));
                if (hovered) {
                    graphics.renderOutline(x, getY(), slotWidth, height, style.accent);
                }
            }
        }

        @Override
        public boolean mousePressed(double mouseX, double mouseY, int button) {
            int slot = slotAt(mouseX);
            if (!isEnabled() || slot < 0 || button > 1) {
                return false;
            }
            change(slot, button == 0 ? 1 : -1);
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            int slot = slotAt(mouseX);
            if (!isEnabled() || slot < 0 || amount == 0) {
                return false;
            }
            change(slot, amount > 0 ? 1 : -1);
            return true;
        }

        private void change(int slot, int step) {
            int value = code.getAsInt();
            onChange.accept(CreeperCode.withSlot(value, slot, CreeperCode.slot(value, slot) + step));
            playClick();
        }
    }

    /** Play position with times; drag (or click) to seek when {@code onSeek} is given. */
    static final class SeekBar extends YmElement {
        private final LongSupplier position;
        private final LongSupplier length;
        private final Consumer<Integer> onSeek;
        private double dragFraction = -1;

        SeekBar(LongSupplier position, LongSupplier length, Consumer<Integer> onSeek) {
            super(100, 18);
            this.position = position;
            this.length = length;
            this.onSeek = onSeek;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YmStyle style = style();
            long total = length.getAsLong();
            long current = dragFraction >= 0 ? (long) (dragFraction * total) : position.getAsLong();
            float fraction = total <= 0 ? 0 : Mth.clamp(current / (float) total, 0, 1);
            int barY = getY() + 2;
            style.drawInset(graphics, getX(), barY, width, 5);
            int fill = Math.round((width - 2) * fraction);
            graphics.fill(getX() + 1, barY + 1, getX() + 1 + fill, barY + 4, style.accent);
            if (total > 0 && isEnabled()) {
                int knob = getX() + 1 + fill;
                graphics.fill(knob - 1, barY - 1, knob + 1, barY + 6, style.text);
            }
            Font font = font();
            graphics.drawString(font, time(current), getX(), getY() + 9, style.textDim, false);
            String end = total > 0 ? time(total) : "--:--";
            graphics.drawString(font, end, getX() + width - font.width(end), getY() + 9, style.textDim, false);
        }

        @Override
        public boolean mousePressed(double mouseX, double mouseY, int button) {
            if (!isEnabled() || button != 0 || length.getAsLong() <= 0) {
                return false;
            }
            dragFraction = fraction(mouseX);
            captureMouse();
            return true;
        }

        @Override
        public void mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (dragFraction >= 0) {
                dragFraction = fraction(mouseX);
            }
        }

        @Override
        public void mouseReleased(double mouseX, double mouseY, int button) {
            if (dragFraction >= 0) {
                onSeek.accept((int) (dragFraction * length.getAsLong() / 20));
                dragFraction = -1;
            }
        }

        private double fraction(double mouseX) {
            return Mth.clamp((mouseX - getX()) / Math.max(1, width), 0, 1);
        }
    }

    /** One song of the library list: left click selects, right click opens its menu, x deletes. */
    static final class SongRow extends YmElement {
        static final int HEIGHT = 24;
        private static final int DELETE_SIZE = 12;
        private final SongInfo song;
        private final BooleanSupplier selected;
        private final Consumer<SongInfo> onSelect;
        private final Consumer<SongInfo> onDelete;
        private final BiConsumer<SongInfo, YmActionMenu> menu;
        private final boolean deletable;

        SongRow(SongInfo song, BooleanSupplier selected, Consumer<SongInfo> onSelect,
                Consumer<SongInfo> onDelete, BiConsumer<SongInfo, YmActionMenu> menu) {
            super(100, HEIGHT);
            this.song = song;
            this.selected = selected;
            this.onSelect = onSelect;
            this.onDelete = onDelete;
            this.menu = menu;
            this.deletable = song.deletable();
            setTooltip(() -> {
                Component title = Component.literal(song.title());
                if (!song.addedBy().isEmpty()) {
                    return List.of(title, Component.translatable("gui.ymtechnicals.creeper_blaster.added_by",
                            song.addedBy()));
                }
                return List.of(title);
            });
        }

        private boolean overDelete(double mouseX, double mouseY) {
            return deletable && mouseX >= getX() + width - DELETE_SIZE - 4 && mouseX < getX() + width - 4
                    && mouseY >= getY() + (HEIGHT - DELETE_SIZE) / 2.0
                    && mouseY < getY() + (HEIGHT + DELETE_SIZE) / 2.0;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YmStyle style = style();
            boolean hovered = isMouseOver(mouseX, mouseY);
            boolean isSelected = selected.getAsBoolean();
            style.drawControl(graphics, getX(), getY(), width, height - 2, hovered, false, true, isSelected);
            Font font = font();
            int textWidth = width - 12 - (deletable ? DELETE_SIZE + 6 : 0);
            graphics.drawString(font, fit(font, song.title(), textWidth), getX() + 5, getY() + 3,
                    isSelected ? style.accent : style.text, false);
            String length = time(song.seconds() * 20L);
            String sub = song.artist().isEmpty() ? length : length + "  ·  " + song.artist();
            graphics.drawString(font, fit(font, sub, textWidth), getX() + 5, getY() + 12, style.textDim, false);
            if (deletable) {
                int dx = getX() + width - DELETE_SIZE - 4;
                int dy = getY() + (HEIGHT - 2 - DELETE_SIZE) / 2;
                boolean overDelete = overDelete(mouseX, mouseY);
                graphics.drawString(font, "✕", dx + 2, dy + 2, overDelete ? 0xFFE05050 : style.textDim, false);
            }
        }

        @Override
        public boolean mousePressed(double mouseX, double mouseY, int button) {
            if (button == 1) {
                YmScreen screen = screen();
                if (screen != null) {
                    YmActionMenu actions = new YmActionMenu();
                    menu.accept(song, actions);
                    actions.open(screen, (int) mouseX, (int) mouseY);
                }
                return true;
            }
            if (button != 0) {
                return false;
            }
            playClick();
            if (overDelete(mouseX, mouseY)) {
                onDelete.accept(song);
            } else {
                onSelect.accept(song);
            }
            return true;
        }
    }

    /** Status line of a blaster screen: last message and a progress bar while downloading. */
    static final class StatusLine extends YmElement {
        private final BlockPos pos;

        StatusLine(BlockPos pos) {
            super(100, 18);
            this.pos = pos;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            CreeperClientState.Status status = CreeperClientState.status(pos);
            if (status == null) {
                return;
            }
            YmStyle style = style();
            Font font = font();
            int textY = getY();
            if (status.progress() >= 0 && status.busy()) {
                style.drawInset(graphics, getX(), getY() + height - 5, width, 5);
                int fill = Math.round((width - 2) * Mth.clamp(status.progress() / 100.0F, 0, 1));
                graphics.fill(getX() + 1, getY() + height - 4, getX() + 1 + fill, getY() + height - 1, style.accent);
            }
            String text = fit(font, status.text().getString(), width);
            graphics.drawString(font, text, getX(), textY + 1, status.busy() ? style.text : style.textDim, false);
        }
    }
}
