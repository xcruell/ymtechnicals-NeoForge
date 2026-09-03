package com.yesmenn.technicals.client.screen;

import com.yesmenn.technicals.client.Soundeffectz3000SoundLibrary;
import com.yesmenn.technicals.client.Soundeffectz3000SoundLibrary.Category;
import com.yesmenn.technicals.network.OpenSoundeffectz3000ScreenPayload;
import com.yesmenn.technicals.network.Soundeffectz3000ActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public class Soundeffectz3000Screen extends Screen {
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 286;
    private static final int VISIBLE_ROWS = 9;

    private final BlockPos pos;
    private final List<ResourceLocation> allSounds = Soundeffectz3000SoundLibrary.sounds();
    private final List<String> namespaces = Soundeffectz3000SoundLibrary.namespaces();
    private List<ResourceLocation> filteredSounds = List.of();
    private ResourceLocation selectedSound;
    private String namespace = "*";
    private String search = "";
    private Category category;
    private int namespaceIndex;
    private int scrollOffset;
    private int volume;
    private int pitch;
    private int range;
    private boolean looping;
    private boolean redstoneEnabled;
    private boolean playing;
    private EditBox searchBox;
    private EditBox volumeBox;
    private EditBox pitchBox;
    private EditBox rangeBox;

    public Soundeffectz3000Screen(OpenSoundeffectz3000ScreenPayload payload) {
        super(Component.translatable("screen.ymtechnicals.soundeffectz_3000"));
        pos = payload.pos();
        selectedSound = ResourceLocation.tryParse(payload.soundId());
        volume = payload.volume();
        pitch = payload.pitch();
        range = payload.range();
        looping = payload.looping();
        redstoneEnabled = payload.redstoneEnabled();
        playing = payload.playing();
        refreshFilter("");
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(10, (height - PANEL_HEIGHT) / 2);
        int listWidth = panelWidth / 2;
        int right = left + listWidth + 12;
        int rightWidth = panelWidth - listWidth - 20;

        searchBox = new EditBox(font, left + 8, top + 32, listWidth - 16, 20, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search sound ID..."));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            if (!search.isBlank()) {
                category = null;
            }
            scrollOffset = 0;
            refreshFilter(value);
//            rebuildWidgets();
//            searchBox.setFocused(true);
//            searchBox.setCursorPosition(search.length());
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal(namespaceLabel()), button -> {
                    namespaceIndex = (namespaceIndex + 1) % (namespaces.size() + 1);
                    namespace = namespaceIndex == 0 ? "*" : namespaces.get(namespaceIndex - 1);
                    category = null;
                    scrollOffset = 0;
                    refreshFilter(search);
                    rebuildWidgets();
                })
                .bounds(left + 8, top + 56, listWidth - 16, 20)
                .build());

        if (category == null && search.isBlank()) {
            Category[] categories = Category.values();
            int categoryWidth = (listWidth - 22) / 2;
            for (int i = 0; i < categories.length; i++) {
                Category nextCategory = categories[i];
                int x = left + 8 + i % 2 * (categoryWidth + 6);
                int y = top + 84 + i / 2 * 36;
                int count = Soundeffectz3000SoundLibrary.count(namespace, nextCategory);
                addRenderableWidget(Button.builder(
                                Component.literal(nextCategory.label() + " (" + count + ")"),
                                button -> {
                                    category = nextCategory;
                                    scrollOffset = 0;
                                    refreshFilter("");
                                    rebuildWidgets();
                                })
                        .bounds(x, y, categoryWidth, 28)
                        .build());
            }
        } else {
            int listTop = top + 80;
            int visibleRows = VISIBLE_ROWS;
            if (category != null && search.isBlank()) {
                addRenderableWidget(Button.builder(
                                Component.literal("< Categories  /  " + category.label()),
                                button -> {
                                    category = null;
                                    scrollOffset = 0;
                                    refreshFilter("");
                                    rebuildWidgets();
                                })
                        .bounds(left + 8, listTop, listWidth - 16, 20)
                        .build());
                listTop += 22;
                visibleRows--;
            }
            int shown = Math.min(visibleRows, Math.max(0, filteredSounds.size() - scrollOffset));
            for (int i = 0; i < shown; i++) {
                ResourceLocation id = filteredSounds.get(scrollOffset + i);
                boolean selected = id.equals(selectedSound);
                addRenderableWidget(Button.builder(
                                Component.literal((selected ? "> " : "") + trim(id.toString(), 39)),
                                button -> {
                                    selectedSound = id;
                                    rebuildWidgets();
                                })
                        .bounds(left + 8, listTop + i * 21, listWidth - 16, 20)
                        .build());
            }
        }

        volumeBox = numericBox(right + 95, top + 62, 62, volume, 3, "Volume");
        pitchBox = numericBox(right + 95, top + 96, 62, pitch, 3, "Pitch");
        rangeBox = numericBox(right + 95, top + 130, 62, range, 3, "Range");
        addStepButtons(right + 164, top + 62, () -> changeVolume(-5), () -> changeVolume(5));
        addStepButtons(right + 164, top + 96, () -> changePitch(-5), () -> changePitch(5));
        addStepButtons(right + 164, top + 130, () -> changeRange(-1), () -> changeRange(1));

        addRenderableWidget(Button.builder(Component.literal(looping ? "Loop: On" : "Loop: Off"), button -> {
                    looping = !looping;
                    rebuildWidgets();
                })
                .bounds(right, top + 170, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(redstoneEnabled ? "Redstone: On" : "Redstone: Off"), button -> {
                    redstoneEnabled = !redstoneEnabled;
                    rebuildWidgets();
                })
                .bounds(right + 102, top + 170, 116, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal(playing ? "Restart" : "Play"), button -> {
                    readValues();
                    playing = looping;
                    send(Soundeffectz3000ActionPayload.PLAY);
                    rebuildWidgets();
                })
                .bounds(right, top + 206, 68, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), button -> {
                    playing = false;
                    send(Soundeffectz3000ActionPayload.STOP);
                    rebuildWidgets();
                })
                .bounds(right + 74, top + 206, 58, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
                    readValues();
                    send(Soundeffectz3000ActionPayload.SAVE);
                })
                .bounds(right + 138, top + 206, 58, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(right + 202, top + 206, Math.max(58, rightWidth - 202), 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(PANEL_WIDTH, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(10, (height - PANEL_HEIGHT) / 2);
        int listWidth = panelWidth / 2;
        int right = left + listWidth + 12;
        graphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xE010141C);
        graphics.fill(left + 5, top + 27, left + listWidth + 3, top + PANEL_HEIGHT - 8, 0xA018202B);
        graphics.fill(right - 5, top + 27, left + panelWidth - 5, top + 238, 0xA018202B);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(font, "Library: " + filteredSounds.size() + " / " + allSounds.size(),
                left + 10, top + 270, 0x8FA9C2, false);
        graphics.drawString(font, "Selected", right, top + 36, 0x8FA9C2, false);
        graphics.drawString(font, trim(selectedSound == null ? "None" : selectedSound.toString(), 42),
                right, top + 48, 0xFFFFFF, false);
        graphics.drawString(font, "Volume (%)", right, top + 68, 0xDCE6F0, false);
        graphics.drawString(font, "Pitch (%)", right, top + 102, 0xDCE6F0, false);
        graphics.drawString(font, "Range", right, top + 136, 0xDCE6F0, false);
        graphics.drawString(font, "Redstone starts playback; loop stops when power is removed.",
                right, top + 240, 0x8FA9C2, false);
        graphics.drawString(font, "Volume 0-200%, pitch 25-400%, range 1-128 blocks.",
                right, top + 253, 0x8FA9C2, false);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = category != null && search.isBlank() ? VISIBLE_ROWS - 1 : VISIBLE_ROWS;
        int max = Math.max(0, filteredSounds.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(scrollY)));
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (searchBox != null && searchBox.isFocused()) {
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        readValues();
        send(Soundeffectz3000ActionPayload.SAVE);
        super.onClose();
    }

    private EditBox numericBox(int x, int y, int width, int value, int maxLength, String label) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setFilter(input -> input.isEmpty() || input.matches("[0-9]+"));
        box.setValue(Integer.toString(value));
        addRenderableWidget(box);
        return box;
    }

    private void addStepButtons(int x, int y, Runnable decrease, Runnable increase) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> decrease.run()).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> increase.run()).bounds(x + 24, y, 20, 20).build());
    }

    private void refreshFilter(String search) {
        String needle = search.toLowerCase(Locale.ROOT).trim();
        List<ResourceLocation> source;
        if (category != null && needle.isEmpty()) {
            source = namespace.equals("*")
                    ? Soundeffectz3000SoundLibrary.sounds(category)
                    : Soundeffectz3000SoundLibrary.sounds(namespace, category);
        } else {
            source = allSounds;
        }
        filteredSounds = source.stream()
                .filter(id -> namespace.equals("*") || id.getNamespace().equals(namespace))
                .filter(id -> needle.isEmpty() || id.toString().contains(needle))
                .toList();
    }

    private String namespaceLabel() {
        return namespace.equals("*") ? "Mod: All" : "Mod: " + namespace;
    }

    private void changeVolume(int delta) {
        volume = clamp(parse(volumeBox, volume) + delta, 0, 200);
        volumeBox.setValue(Integer.toString(volume));
    }

    private void changePitch(int delta) {
        pitch = clamp(parse(pitchBox, pitch) + delta, 25, 400);
        pitchBox.setValue(Integer.toString(pitch));
    }

    private void changeRange(int delta) {
        range = clamp(parse(rangeBox, range) + delta, 1, 128);
        rangeBox.setValue(Integer.toString(range));
    }

    private void readValues() {
        volume = clamp(parse(volumeBox, volume), 0, 200);
        pitch = clamp(parse(pitchBox, pitch), 25, 400);
        range = clamp(parse(rangeBox, range), 1, 128);
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new Soundeffectz3000ActionPayload(
                pos,
                action,
                selectedSound == null ? "" : selectedSound.toString(),
                volume,
                pitch,
                range,
                looping,
                redstoneEnabled));
    }

    private static int parse(EditBox box, int fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }
}
