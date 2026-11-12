package com.yesmenn.technicals.client.screen;

import com.yesmenn.ducttape.client.kit.YmContainer;
import com.yesmenn.ducttape.client.kit.YmElement;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.YmScrollBox;
import com.yesmenn.ducttape.client.kit.element.YmButton;
import com.yesmenn.ducttape.client.kit.element.YmSelect;
import com.yesmenn.ducttape.client.kit.element.YmText;
import com.yesmenn.ducttape.client.kit.element.YmTextField;
import com.yesmenn.ducttape.client.kit.element.YmWindow;
import com.yesmenn.ducttape.client.kit.layout.YmLayout;
import com.yesmenn.ducttape.client.kit.settings.YmRows;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.technicals.block.entity.Soundeffectz3000BlockEntity;
import com.yesmenn.technicals.client.Soundeffectz3000SoundLibrary;
import com.yesmenn.technicals.client.Soundeffectz3000SoundLibrary.Category;
import com.yesmenn.technicals.network.OpenSoundeffectz3000ScreenPayload;
import com.yesmenn.technicals.network.Soundeffectz3000ActionPayload;
import com.yesmenn.technicals.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Soundeffectz3000 screen (YM kit): the sound list with search, mod and category filter on the left (right click
 * plays a sound only for you), the chosen sound with play/stop and its settings on the right. Settings go to the
 * block with "Play" and when the screen closes.
 */
public class Soundeffectz3000Screen extends YmScreen {
    /** More rows make the list slow to draw; the search narrows it down. */
    private static final int MAX_SHOWN = 400;
    private static final String ALL = "*";

    private final BlockPos pos;
    @Nullable
    private ResourceLocation selected;
    private String search = "";
    private String namespace = ALL;
    private String category = ALL;
    private int volume;
    private int pitch;
    private int range;
    private boolean looping;
    private boolean redstoneEnabled;
    private YmScrollBox list;
    private YmText countText;
    private String builtFilter = "";

    public Soundeffectz3000Screen(OpenSoundeffectz3000ScreenPayload payload) {
        super(Component.translatable("screen.ymtechnicals.soundeffectz_3000"), null);
        pos = payload.pos();
        selected = ResourceLocation.tryParse(payload.soundId());
        volume = payload.volume();
        pitch = payload.pitch();
        range = payload.range();
        looping = payload.looping();
        redstoneEnabled = payload.redstoneEnabled();
        setAccent(YmStyle.ACCENT_TECHNICALS);
    }

    @Override
    protected void build(YmContainer root) {
        int w = Math.min(520, width - 16);
        int h = Math.min(320, height - 16);
        YmWindow window = root.add(new YmWindow(getTitle(), w, h)).closable();
        window.centerIn(root);
        window.footer().add(new YmButton(Component.translatable("gui.done"), button -> onClose()).primary());

        YmContainer content = window.content();
        content.setPadding(4);
        YmContainer left = content.add(new YmContainer());
        YmScrollBox right = content.add(new YmScrollBox());
        content.setLayout(panel -> {
            int leftWidth = panel.contentWidth() * 11 / 20;
            left.setBounds(0, 0, leftWidth, panel.contentHeight());
            right.setBounds(leftWidth + 4, 0, panel.contentWidth() - leftWidth - 4, panel.contentHeight());
        });
        buildList(left);
        buildSettings(right);
    }

    private void buildList(YmContainer left) {
        YmTextField searchField = left.add(new YmTextField(search, text -> search = text));
        searchField.setHint(tr("search"));
        List<String> namespaces = new ArrayList<>();
        namespaces.add(ALL);
        namespaces.addAll(Soundeffectz3000SoundLibrary.namespaces());
        YmSelect<String> modSelect = left.add(new YmSelect<>(namespaces, namespace,
                value -> value.equals(ALL) ? tr("all_mods") : Component.literal(value), value -> namespace = value));
        List<String> categories = new ArrayList<>();
        categories.add(ALL);
        for (Category value : Category.values()) {
            categories.add(value.name());
        }
        YmSelect<String> categorySelect = left.add(new YmSelect<>(categories, category,
                value -> value.equals(ALL) ? tr("all_categories") : Component.literal(Category.valueOf(value).label()),
                value -> category = value));
        list = left.add(new YmScrollBox());
        list.setBackground(YmContainer.Background.INSET);
        list.setPadding(3, 3, YmScrollBox.BAR_WIDTH + 4, 3);
        list.setLayout(YmLayout.column(1));
        YmText count = left.add(new YmText(Component.empty()).dim());
        countText = count;
        left.setLayout(panel -> {
            int w = panel.contentWidth();
            searchField.setBounds(0, 1, w, 18);
            modSelect.setBounds(0, 22, (w - 3) / 2, YmButton.DEFAULT_HEIGHT);
            categorySelect.setBounds((w - 3) / 2 + 3, 22, w - (w - 3) / 2 - 3, YmButton.DEFAULT_HEIGHT);
            list.setBounds(0, 46, w, Math.max(20, panel.contentHeight() - 46 - 14));
            count.setBounds(0, panel.contentHeight() - 10, w, 10);
        });
        fillList(count);
    }

    private void buildSettings(YmScrollBox right) {
        right.setPadding(2, 0, YmScrollBox.BAR_WIDTH + 3, 2).setLayout(YmLayout.column(3));
        right.add(new YmText(tr("selected")).dim());
        right.add(new YmText(() -> Component.literal(selected == null ? "-" : selected.toString())));
        YmContainer buttons = right.add(new YmContainer(0, YmButton.DEFAULT_HEIGHT));
        buttons.setLayout(YmLayout.rowEqual(3));
        buttons.add(new YmButton(tr("play"), button -> send(Soundeffectz3000ActionPayload.PLAY)).primary());
        buttons.add(new YmButton(tr("stop"), button -> send(Soundeffectz3000ActionPayload.STOP)));
        right.add(new YmText(Component.empty()));
        YmRows.intSlider(right, tr("volume"), 0, 200, () -> volume, value -> volume = value,
                Soundeffectz3000BlockEntity.DEFAULT_VOLUME);
        YmRows.intSlider(right, tr("pitch"), 25, 400, () -> pitch, value -> pitch = value,
                Soundeffectz3000BlockEntity.DEFAULT_PITCH);
        YmRows.intSlider(right, tr("range"), 1, 128, () -> range, value -> range = value,
                Soundeffectz3000BlockEntity.DEFAULT_RANGE);
        YmRows.toggle(right, tr("loop"), () -> looping, on -> looping = on, false);
        YmRows.toggle(right, tr("redstone"), () -> redstoneEnabled, on -> redstoneEnabled = on, true)
                .label().setTooltip(tr("redstone.tooltip"));
        right.add(new YmText(tr("help")).wrap().dim());
    }

    private String filterKey() {
        return search.trim().toLowerCase(Locale.ROOT) + "|" + namespace + "|" + category;
    }

    /** (Re)fills the list from the search text and both filters. */
    private void fillList(YmText count) {
        for (YmElement child : new ArrayList<>(list.children())) {
            list.remove(child);
        }
        builtFilter = filterKey();
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> source;
        if (category.equals(ALL)) {
            source = Soundeffectz3000SoundLibrary.sounds();
        } else if (namespace.equals(ALL)) {
            source = Soundeffectz3000SoundLibrary.sounds(Category.valueOf(category));
        } else {
            source = Soundeffectz3000SoundLibrary.sounds(namespace, Category.valueOf(category));
        }
        List<ResourceLocation> found = source.stream()
                .filter(id -> namespace.equals(ALL) || id.getNamespace().equals(namespace))
                .filter(id -> needle.isEmpty() || id.toString().contains(needle))
                .toList();
        for (ResourceLocation id : found.subList(0, Math.min(MAX_SHOWN, found.size()))) {
            list.add(new SoundRow(id)).setSize(0, SoundRow.HEIGHT);
        }
        if (found.isEmpty()) {
            list.add(new YmText(tr("nothing_found")).dim());
        }
        count.setText(found.size() > MAX_SHOWN
                ? Component.translatable("gui.ymtechnicals.soundeffectz_3000.count_more", MAX_SHOWN, found.size())
                : Component.translatable("gui.ymtechnicals.soundeffectz_3000.count", found.size(),
                Soundeffectz3000SoundLibrary.sounds().size()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!builtFilter.equals(filterKey())) {
            // Only the list: rebuilding the whole screen would take the focus from the search field.
            fillList(countText);
            relayout();
        }
    }

    @Override
    public void removed() {
        send(Soundeffectz3000ActionPayload.SAVE);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(int action) {
        ClientPlatform.sendToServer(new Soundeffectz3000ActionPayload(pos, action,
                selected == null ? "" : selected.toString(), volume, pitch, range, looping, redstoneEnabled));
    }

    /** Plays the sound only on this client, with the chosen pitch and volume. */
    private void listen(ResourceLocation id) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(id), pitch / 100.0F, Math.min(1.0F, volume / 100.0F)));
    }

    private static MutableComponent tr(String key) {
        return Component.translatable("gui.ymtechnicals.soundeffectz_3000." + key);
    }

    /** One sound of the list: left click selects it, right click plays it for you. */
    private final class SoundRow extends YmElement {
        static final int HEIGHT = 14;
        private final ResourceLocation id;

        SoundRow(ResourceLocation id) {
            super(100, HEIGHT);
            this.id = id;
            setTooltip(Component.literal(id.toString()), tr("row.hint"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YmStyle style = style();
            boolean isSelected = id.equals(selected);
            boolean hovered = isMouseOver(mouseX, mouseY);
            if (isSelected || hovered) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height,
                        isSelected ? YmStyle.withAlpha(style.accent, 0x60) : 0x30FFFFFF);
            }
            Font font = font();
            String text = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
            if (font.width(text) > width - 6) {
                text = font.plainSubstrByWidth(text, width - 6 - font.width("…")) + "…";
            }
            graphics.drawString(font, text, getX() + 3, getY() + 3, isSelected ? style.accent : style.text, false);
        }

        @Override
        public boolean mousePressed(double mouseX, double mouseY, int button) {
            if (button == 1) {
                listen(id);
                return true;
            }
            if (button == 0) {
                selected = id;
                playClick();
                return true;
            }
            return false;
        }
    }
}
