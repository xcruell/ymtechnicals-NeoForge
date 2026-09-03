package com.yesmenn.technicals.client.screen;

import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.network.OpenObserversEyeScreenPayload;
import com.yesmenn.technicals.network.ObserversEyeActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ObserversEyeScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 390;
    private static final int MAX_SIZE = 64;
    private static final int MAX_OFFSET = 6;

    private final BlockPos pos;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private int pattern;
    private int outputMode;
    private int filterMode;
    private boolean preview;
    private final Set<String> players = new LinkedHashSet<>();
    private EditBox playerBox;

    public ObserversEyeScreen(OpenObserversEyeScreenPayload payload) {
        super(Component.translatable("screen.ymtechnicals.observers_eye"));
        pos = payload.pos();
        sizeX = payload.sizeX();
        sizeY = payload.sizeY();
        sizeZ = payload.sizeZ();
        offsetX = payload.offsetX();
        offsetY = payload.offsetY();
        offsetZ = payload.offsetZ();
        pattern = payload.pattern();
        outputMode = payload.outputMode();
        filterMode = payload.filterMode();
        preview = payload.preview();
        setPlayers(payload.players());
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(10, (height - PANEL_HEIGHT) / 2);
        int row = top + 62;

        addValueRow(left + 24, row, "Range", Math.max(sizeX, sizeZ), 1, MAX_SIZE, value -> {
            sizeX = value;
            sizeZ = value;
        });
        row += 26;
        addValueRow(left + 24, row, "Size X", sizeX, 1, MAX_SIZE, value -> sizeX = value);
        row += 24;
        addValueRow(left + 24, row, "Size Y", sizeY, 1, MAX_SIZE, value -> sizeY = value);
        row += 24;
        addValueRow(left + 24, row, "Size Z", sizeZ, 1, MAX_SIZE, value -> sizeZ = value);

        row = top + 88;
        addValueRow(left + 318, row, "Offset X", offsetX, -MAX_OFFSET, MAX_OFFSET, value -> offsetX = value);
        row += 24;
        addValueRow(left + 318, row, "Offset Y", offsetY, -MAX_OFFSET, MAX_OFFSET, value -> offsetY = value);
        row += 24;
        addValueRow(left + 318, row, "Offset Z", offsetZ, -MAX_OFFSET, MAX_OFFSET, value -> offsetZ = value);

        addRenderableWidget(Button.builder(Component.literal(patternLabel()), button -> {
                    pattern = pattern == ObserversEyeBlockEntity.PATTERN_BOX
                            ? ObserversEyeBlockEntity.PATTERN_CYLINDER
                            : ObserversEyeBlockEntity.PATTERN_BOX;
                    send();
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Box checks the full cuboid. Cylinder checks an oval X/Z footprint with the configured Y height.")))
                .bounds(left + 24, top + 214, 162, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(outputLabel()), button -> {
                    outputMode = outputMode == ObserversEyeBlockEntity.MODE_ON_OFF
                            ? ObserversEyeBlockEntity.MODE_PULSE
                            : ObserversEyeBlockEntity.MODE_ON_OFF;
                    send();
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Pulse triggers once for each new entity entering the zone. On/Off keeps a redstone signal active while any matching entity is detected.")))
                .bounds(left + 198, top + 214, 162, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(preview ? "Preview: On" : "Preview: Off"), button -> {
                    preview = !preview;
                    send();
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Toggles the detection zone preview render in the world.")))
                .bounds(left + 372, top + 214, 162, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal(filterLabel()), button -> {
                    filterMode = (filterMode + 1) % 3;
                    send();
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Cycles who can trigger the sensor: players, mobs and animals, or only manually added players.")))
                .bounds(left + 24, top + 260, 210, 20)
                .build());

        playerBox = new EditBox(font, left + 246, top + 260, 126, 20, Component.literal("Player"));
        playerBox.setMaxLength(16);
        playerBox.setHint(Component.literal("Player name"));
        addRenderableWidget(playerBox);
        addRenderableWidget(Button.builder(Component.literal("Add player"), button -> {
                    addPlayer(playerBox.getValue());
                    playerBox.setValue("");
                    filterMode = ObserversEyeBlockEntity.FILTER_ADDED_PLAYERS;
                    send();
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Adds the typed player name to the Added Players filter. Names can be added while offline.")))
                .bounds(left + 382, top + 260, 152, 20)
                .build());

        List<String> online = onlinePlayers();
        int shownOnline = Math.min(4, online.size());
        for (int i = 0; i < shownOnline; i++) {
            String name = online.get(i);
            addRenderableWidget(Button.builder(Component.literal("+ " + name), button -> {
                        addPlayer(name);
                        filterMode = ObserversEyeBlockEntity.FILTER_ADDED_PLAYERS;
                        send();
                        rebuildWidgets();
                    })
                    .tooltip(Tooltip.create(Component.literal("Add this online player to the sensor allowlist.")))
                    .bounds(left + 24 + i * 128, top + 306, 122, 20)
                    .build());
        }

        int playerIndex = 0;
        for (String name : players) {
            if (playerIndex >= 4) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal(name + " x"), button -> {
                        players.remove(name);
                        send();
                        rebuildWidgets();
                    })
                    .tooltip(Tooltip.create(Component.literal("Remove this player from the sensor allowlist.")))
                    .bounds(left + 24 + playerIndex * 128, top + 344, 122, 20)
                    .build());
            playerIndex++;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.yesmenn.save"), button -> {
                    send();
                    onClose();
                })
                .bounds(left + PANEL_WIDTH - 142, top + PANEL_HEIGHT - 26, 64, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.yesmenn.close"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 72, top + PANEL_HEIGHT - 26, 54, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(10, (height - PANEL_HEIGHT) / 2);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE010141C);
        drawCard(graphics, left + 12, top + 38, 264, 136, 0x6680D0FF);
        drawCard(graphics, left + 306, top + 64, 230, 110, 0x6680D0FF);
        drawCard(graphics, left + 12, top + 190, PANEL_WIDTH - 24, 184, 0x6630C060);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(font, "Detection Zone", left + 24, top + 46, 0x8FA9C2, false);
        graphics.drawString(font, "Offset", left + 318, top + 72, 0x8FA9C2, false);
        graphics.drawString(font, "Mode & Filter", left + 24, top + 198, 0x8FA9C2, false);
        graphics.drawString(font, "Shift changes values by 4", left + 318, top + 160, 0x667A90, false);
        graphics.drawString(font, "Filters", left + 24, top + 244, 0x8FA9C2, false);
        graphics.drawString(font, "Online players", left + 24, top + 290, 0x8FA9C2, false);
        graphics.drawString(font, "Added players", left + 24, top + 328,
                players.isEmpty() ? 0x777777 : 0x8FA9C2, false);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        send();
        super.onClose();
    }

    public boolean edits(BlockPos blockPos) {
        return pos.equals(blockPos);
    }

    public boolean previewEnabled() {
        return preview;
    }

    public boolean previewCylinder() {
        return pattern == ObserversEyeBlockEntity.PATTERN_CYLINDER;
    }

    public AABB previewDetectionBox() {
        double centerX = pos.getX() + 0.5D + offsetX;
        double centerY = pos.getY() + 0.5D + offsetY;
        double centerZ = pos.getZ() + 0.5D + offsetZ;
        return new AABB(
                centerX - sizeX / 2.0D, centerY - sizeY / 2.0D, centerZ - sizeZ / 2.0D,
                centerX + sizeX / 2.0D, centerY + sizeY / 2.0D, centerZ + sizeZ / 2.0D);
    }

    private void addValueRow(int x, int y, String label, int value, int min, int max, IntSetter setter) {
        graphicsLabel(x, y, label);
        addRenderableWidget(Button.builder(Component.literal("-"), button -> {
                    setter.set(clamp(value - step(), min, max));
                    send();
                    rebuildWidgets();
                })
                .bounds(x + 74, y, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(Integer.toString(value)), button -> {
                })
                .bounds(x + 98, y, 54, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    setter.set(clamp(value + step(), min, max));
                    send();
                    rebuildWidgets();
                })
                .bounds(x + 156, y, 20, 20)
                .build());
    }

    private void graphicsLabel(int x, int y, String label) {
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) ->
                graphics.drawString(font, label, x, y + 6, 0xDCE6F0, false));
    }

    private int step() {
        return hasShiftDown() ? 4 : 1;
    }

    private String patternLabel() {
        return pattern == ObserversEyeBlockEntity.PATTERN_CYLINDER ? "Pattern: Cylinder" : "Pattern: Box";
    }

    private String outputLabel() {
        return outputMode == ObserversEyeBlockEntity.MODE_PULSE ? "Output: Pulse" : "Output: On/Off";
    }

    private String filterLabel() {
        return switch (filterMode) {
            case ObserversEyeBlockEntity.FILTER_MOBS_ANIMALS -> "Filter: Mobs & Animals";
            case ObserversEyeBlockEntity.FILTER_ADDED_PLAYERS -> "Filter: Added Players";
            default -> "Filter: Players";
        };
    }

    private void addPlayer(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (!cleaned.isBlank()) {
            players.add(cleaned.toLowerCase(Locale.ROOT));
        }
    }

    private List<String> onlinePlayers() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        minecraft.level.players().forEach(player -> names.add(player.getGameProfile().getName()));
        return names;
    }

    private void setPlayers(String value) {
        players.clear();
        if (value == null || value.isBlank()) {
            return;
        }
        for (String player : value.split(",")) {
            addPlayer(player);
        }
    }

    private String playerListString() {
        return String.join(",", players);
    }

    private void send() {
        PacketDistributor.sendToServer(new ObserversEyeActionPayload(
                pos, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                pattern, outputMode, filterMode, preview, playerListString()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void drawCard(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x, y, x + width, y + height, 0x55181818);
        graphics.renderOutline(x, y, width, height, 0x55FFFFFF);
        graphics.fill(x, y, x + 2, y + height, accent);
    }

    private interface IntSetter {
        void set(int value);
    }
}
