package com.yesmenn.technicals.client.screen;

import com.yesmenn.ducttape.client.kit.YmContainer;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.YmScrollBox;
import com.yesmenn.ducttape.client.kit.element.YmButton;
import com.yesmenn.ducttape.client.kit.element.YmFormRow;
import com.yesmenn.ducttape.client.kit.element.YmHeading;
import com.yesmenn.ducttape.client.kit.element.YmIconButton;
import com.yesmenn.ducttape.client.kit.element.YmSlider;
import com.yesmenn.ducttape.client.kit.element.YmText;
import com.yesmenn.ducttape.client.kit.element.YmTextField;
import com.yesmenn.ducttape.client.kit.element.YmWindow;
import com.yesmenn.ducttape.client.kit.layout.YmLayout;
import com.yesmenn.ducttape.client.kit.settings.YmRows;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.network.ObserversEyeActionPayload;
import com.yesmenn.technicals.network.OpenObserversEyeScreenPayload;
import com.yesmenn.technicals.platform.ClientPlatform;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Observers Eye screen (YM kit): detection zone, output and filter. The world stays visible and the window fades out
 * while a slider is dragged, so the zone preview in the world can be watched while it changes. Changes are sent while
 * editing (at most every 120 ms) and when the screen closes.
 */
public class ObserversEyeScreen extends YmScreen {
    private static final long SEND_INTERVAL_MILLIS = 120;

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
    private String playerInput = "";
    private boolean dirty;
    private long lastSend;
    private int pageScroll;
    private YmScrollBox page;
    private YmSlider sliderX;
    private YmSlider sliderZ;

    public ObserversEyeScreen(OpenObserversEyeScreenPayload payload) {
        super(Component.translatable("screen.ymtechnicals.observers_eye"), null);
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
        if (payload.players() != null) {
            for (String player : payload.players().split(",")) {
                addPlayer(player);
            }
        }
        setAccent(YmStyle.ACCENT_TECHNICALS);
        setShowWorld(true);
        setFadeWhileDragging(true);
    }

    @Override
    protected void build(YmContainer root) {
        if (page != null) {
            pageScroll = page.getScroll();
        }
        int w = Math.min(400, width - 16);
        int h = Math.min(330, height - 16);
        YmWindow window = root.add(new YmWindow(getTitle(), w, h)).closable();
        window.centerIn(root);
        window.footer().add(new YmButton(Component.translatable("gui.done"), button -> onClose()).primary());

        page = window.content().add(new YmScrollBox());
        window.content().setPadding(4).setLayout(YmLayout.fill());
        page.setPadding(2, 2, YmScrollBox.BAR_WIDTH + 3, 2).setLayout(YmLayout.column(3));

        zone(page);
        output(page);
        filter(page);
    }

    @Override
    protected void init() {
        super.init();
        page.setScroll(pageScroll);
    }

    private void zone(YmContainer page) {
        page.add(new YmHeading(tr("zone")));
        YmRows.choice(page, tr("pattern"), List.of(ObserversEyeBlockEntity.PATTERN_BOX,
                        ObserversEyeBlockEntity.PATTERN_CYLINDER), () -> pattern, value -> change(() -> pattern = value),
                value -> tr(value == ObserversEyeBlockEntity.PATTERN_CYLINDER ? "pattern.cylinder" : "pattern.box"),
                ObserversEyeBlockEntity.PATTERN_BOX).label().setTooltip(tr("pattern.tooltip"));
        // Width sets X and Z together (the usual square or round zone).
        slider(page, tr("width"), 1, ObserversEyeBlockEntity.MAX_SIZE, () -> Math.max(sizeX, sizeZ), value -> {
            sizeX = value;
            sizeZ = value;
            sliderX.setValue(value);
            sliderZ.setValue(value);
        }, ObserversEyeBlockEntity.DEFAULT_SIZE_XZ);
        sliderX = slider(page, tr("size_x"), 1, ObserversEyeBlockEntity.MAX_SIZE, () -> sizeX, value -> sizeX = value,
                ObserversEyeBlockEntity.DEFAULT_SIZE_XZ);
        slider(page, tr("size_y"), 1, ObserversEyeBlockEntity.MAX_SIZE, () -> sizeY, value -> sizeY = value,
                ObserversEyeBlockEntity.DEFAULT_SIZE_Y);
        sliderZ = slider(page, tr("size_z"), 1, ObserversEyeBlockEntity.MAX_SIZE, () -> sizeZ, value -> sizeZ = value,
                ObserversEyeBlockEntity.DEFAULT_SIZE_XZ);
        int maxOffset = ObserversEyeBlockEntity.MAX_OFFSET;
        slider(page, tr("offset_x"), -maxOffset, maxOffset, () -> offsetX, value -> offsetX = value, 0);
        slider(page, tr("offset_y"), -maxOffset, maxOffset, () -> offsetY, value -> offsetY = value, 0);
        slider(page, tr("offset_z"), -maxOffset, maxOffset, () -> offsetZ, value -> offsetZ = value, 0);
    }

    private void output(YmContainer page) {
        page.add(new YmHeading(tr("output")));
        YmRows.choice(page, tr("output.mode"), List.of(ObserversEyeBlockEntity.MODE_ON_OFF,
                        ObserversEyeBlockEntity.MODE_PULSE), () -> outputMode, value -> change(() -> outputMode = value),
                value -> tr(value == ObserversEyeBlockEntity.MODE_PULSE ? "output.pulse" : "output.on_off"),
                ObserversEyeBlockEntity.MODE_ON_OFF).label().setTooltip(tr("output.tooltip"));
        YmRows.toggle(page, tr("preview"), () -> preview, on -> change(() -> preview = on), false)
                .label().setTooltip(tr("preview.tooltip"));
    }

    private void filter(YmContainer page) {
        page.add(new YmHeading(tr("filter")));
        YmRows.choice(page, tr("filter.who"), List.of(ObserversEyeBlockEntity.FILTER_PLAYERS,
                        ObserversEyeBlockEntity.FILTER_MOBS_ANIMALS, ObserversEyeBlockEntity.FILTER_ADDED_PLAYERS),
                () -> filterMode, value -> change(() -> filterMode = value), value -> tr(switch (value) {
                    case ObserversEyeBlockEntity.FILTER_MOBS_ANIMALS -> "filter.mobs";
                    case ObserversEyeBlockEntity.FILTER_ADDED_PLAYERS -> "filter.added";
                    default -> "filter.players";
                }), ObserversEyeBlockEntity.FILTER_PLAYERS).label().setTooltip(tr("filter.tooltip"));

        // Add by name (also offline players).
        YmContainer addRow = page.add(new YmContainer(0, YmButton.DEFAULT_HEIGHT));
        YmTextField nameField = addRow.add(new YmTextField(playerInput, text -> playerInput = text));
        nameField.setMaxLength(16);
        nameField.setFilter(text -> text.matches("[A-Za-z0-9_]*"));
        nameField.setHint(tr("player.name"));
        nameField.setOnEnter(text -> addAndRebuild(text));
        YmButton addButton = addRow.add(new YmButton(tr("player.add"), button -> addAndRebuild(playerInput)));
        addButton.setTooltip(tr("player.add.tooltip"));
        addRow.setLayout(panel -> {
            int buttonWidth = Math.max(70, font.width(addButton.getText()) + 12);
            nameField.setBounds(0, 1, panel.contentWidth() - buttonWidth - 3, 18);
            addButton.setBounds(panel.contentWidth() - buttonWidth, 0, buttonWidth, YmButton.DEFAULT_HEIGHT);
        });

        // Online players not on the list yet, three per row.
        List<String> online = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.level.players().forEach(player -> {
                String name = player.getGameProfile().getName();
                if (!players.contains(name.toLowerCase(Locale.ROOT))) {
                    online.add(name);
                }
            });
        }
        if (!online.isEmpty()) {
            page.add(new YmText(tr("player.online")).dim());
            for (int i = 0; i < online.size(); i += 3) {
                YmContainer row = page.add(new YmContainer(0, YmButton.DEFAULT_HEIGHT));
                row.setLayout(YmLayout.rowEqual(3));
                for (String name : online.subList(i, Math.min(i + 3, online.size()))) {
                    row.add(new YmButton(Component.literal("+ " + name), button -> addAndRebuild(name)));
                }
                // Keep the buttons the same width in a short last row.
                for (int fill = online.size() - i; fill < 3; fill++) {
                    row.add(new YmText(Component.empty()));
                }
            }
        }

        page.add(new YmText(players.isEmpty() ? tr("player.none") : tr("player.list")).dim());
        for (String name : players) {
            YmIconButton remove = new YmIconButton(YmIconButton.Icon.DELETE, button -> {
                players.remove(name);
                dirty = true;
                rebuild();
            });
            remove.setTooltip(tr("player.remove"));
            YmContainer row = page.add(new YmContainer(0, 14));
            YmText text = row.add(new YmText(Component.literal("• " + name)));
            row.add(remove);
            row.setLayout(panel -> {
                text.setBounds(4, 2, panel.contentWidth() - 20, 10);
                remove.setBounds(panel.contentWidth() - 12, 1, 12, 12);
            });
        }
    }

    private YmSlider slider(YmContainer page, Component label, int min, int max, IntSupplier get, IntConsumer set,
                            int defaultValue) {
        YmSlider slider = YmSlider.ofInt(Component.empty(), min, max, get.getAsInt(), value -> {
            set.accept(value);
            dirty = true;
        });
        YmFormRow row = page.add(new YmFormRow(label, YmRows.LABEL_WIDTH, slider));
        row.resettable(() -> get.getAsInt() != defaultValue, () -> {
            set.accept(defaultValue);
            slider.setValue(get.getAsInt());
            dirty = true;
        });
        return slider;
    }

    private void change(Runnable change) {
        change.run();
        dirty = true;
    }

    private void addAndRebuild(String name) {
        if (addPlayer(name)) {
            playerInput = "";
            filterMode = ObserversEyeBlockEntity.FILTER_ADDED_PLAYERS;
            dirty = true;
            rebuild();
        }
    }

    private boolean addPlayer(String name) {
        String cleaned = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return !cleaned.isBlank() && players.add(cleaned);
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty && Util.getMillis() - lastSend >= SEND_INTERVAL_MILLIS) {
            send();
        }
    }

    @Override
    public void removed() {
        send();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- live zone preview, read by ObserversEyeRenderer ---

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

    private void send() {
        ClientPlatform.sendToServer(new ObserversEyeActionPayload(pos, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                pattern, outputMode, filterMode, preview, String.join(",", players)));
        dirty = false;
        lastSend = Util.getMillis();
    }

    private static MutableComponent tr(String key) {
        return Component.translatable("gui.ymtechnicals.observers_eye." + key);
    }
}
