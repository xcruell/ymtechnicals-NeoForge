package com.yesmenn.technicals.creeper.client.screen;

import com.yesmenn.ducttape.client.kit.YmContainer;
import com.yesmenn.ducttape.client.kit.YmElement;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.YmScrollBox;
import com.yesmenn.ducttape.client.kit.element.YmActionMenu;
import com.yesmenn.ducttape.client.kit.element.YmButton;
import com.yesmenn.ducttape.client.kit.element.YmDialog;
import com.yesmenn.ducttape.client.kit.element.YmFormRow;
import com.yesmenn.ducttape.client.kit.element.YmSlider;
import com.yesmenn.ducttape.client.kit.element.YmSwitch;
import com.yesmenn.ducttape.client.kit.element.YmText;
import com.yesmenn.ducttape.client.kit.element.YmTextField;
import com.yesmenn.ducttape.client.kit.element.YmWindow;
import com.yesmenn.ducttape.client.kit.layout.YmLayout;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.client.CreeperClientState;
import com.yesmenn.technicals.creeper.client.CreeperUploads;
import com.yesmenn.technicals.creeper.client.SongCache;
import com.yesmenn.technicals.creeper.network.ChannelState;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import com.yesmenn.technicals.creeper.network.CreeperPayloads.BlasterAction.Action;
import com.yesmenn.technicals.creeper.network.SongInfo;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.yesmenn.technicals.platform.ClientPlatform;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CreeperBlaster3000 screen (YM kit): player and settings on the left, the song library with search and the download
 * field on the right. All values are read live from the block entity; changes go to the server as actions.
 */
public class CreeperBlasterScreen extends YmScreen {
    private static final int LEFT_WIDTH = 178;
    private static final int LABEL_WIDTH = 52;
    /** Sliders send at most this often while dragged. */
    private static final long SEND_INTERVAL_MILLIS = 120;

    private final BlockPos pos;
    private String search = "";
    private String link;
    private int builtLibraryVersion = -1;
    private String builtSearch = "";
    private YmScrollBox list;
    private int pendingVolume = -1;
    private int pendingRange = -1;
    private long lastSend;

    public CreeperBlasterScreen(BlockPos pos) {
        super(Component.translatable("block.ymtechnicals.creeper_blaster_3000"), null);
        this.pos = pos.immutable();
        CreeperBlaster3000BlockEntity blaster = blaster();
        this.link = blaster == null ? "" : blaster.link();
        setAccent(YmStyle.ACCENT_TECHNICALS);
        send(Action.OPEN, 0, "");
        SongCache.scan();
    }

    @Nullable
    private CreeperBlaster3000BlockEntity blaster() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof CreeperBlaster3000BlockEntity
                blaster ? blaster : null;
    }

    private void send(Action action, int value, String text) {
        ClientPlatform.sendToServer(new CreeperPayloads.BlasterAction(pos, action, value, text));
    }

    private long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    @Override
    protected void build(YmContainer root) {
        CreeperBlaster3000BlockEntity blaster = blaster();
        int w = Math.min(460, width - 16);
        int h = Math.min(310, height - 16);
        YmWindow window = root.add(new YmWindow(getTitle(), w, h)).closable();
        window.centerIn(root);
        window.footer().add(new YmButton(Component.translatable("gui.done"), button -> onClose()).primary());

        YmContainer content = window.content();
        content.setPadding(4);
        // Scrolls on small windows.
        YmScrollBox left = content.add(new YmScrollBox());
        YmContainer right = content.add(new YmContainer());
        content.setLayout(panel -> {
            int leftWidth = Math.min(LEFT_WIDTH, panel.contentWidth() / 2);
            left.setBounds(0, 0, leftWidth, panel.contentHeight());
            right.setBounds(leftWidth + 4, 0, panel.contentWidth() - leftWidth - 4, panel.contentHeight());
        });
        buildPlayer(left, blaster);
        buildLibrary(right);
    }

    private void buildPlayer(YmContainer left, @Nullable CreeperBlaster3000BlockEntity initial) {
        left.setBackground(YmContainer.Background.INSET).setPadding(5, 5, YmScrollBox.BAR_WIDTH + 3, 5)
                .setLayout(YmLayout.column(4));
        left.add(new YmText(() -> {
            CreeperBlaster3000BlockEntity blaster = blaster();
            return blaster == null || blaster.songId().isEmpty()
                    ? Component.translatable("gui.ymtechnicals.creeper_blaster.no_song")
                    : Component.literal(blaster.title());
        }));
        left.add(new YmText(() -> Component.translatable(switch (mode()) {
            case PLAYING -> "gui.ymtechnicals.creeper_blaster.playing";
            case PAUSED -> "gui.ymtechnicals.creeper_blaster.paused";
            default -> "gui.ymtechnicals.creeper_blaster.stopped";
        })).dim());
        left.add(new CreeperWidgets.SeekBar(
                () -> {
                    CreeperBlaster3000BlockEntity blaster = blaster();
                    return blaster == null ? 0 : blaster.positionTicks(gameTime());
                },
                () -> {
                    CreeperBlaster3000BlockEntity blaster = blaster();
                    return blaster == null ? 0 : blaster.songTicks();
                },
                seconds -> send(Action.SEEK, seconds, "")));

        YmContainer transport = left.add(new YmContainer(0, YmButton.DEFAULT_HEIGHT));
        transport.setLayout(YmLayout.rowEqual(3));
        transport.add(new YmButton(Component.literal("« 10"), button -> seekBy(-10)))
                .setTooltip(Component.translatable("gui.ymtechnicals.creeper_blaster.back"));
        transport.add(new YmButton(Component.literal("▶"), button -> send(
                mode() == ChannelState.Mode.PLAYING ? Action.PAUSE : Action.PLAY, 0, "")) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                               float partialTick) {
                setText(Component.literal(mode() == ChannelState.Mode.PLAYING ? "❚❚" : "▶"));
                super.render(graphics, mouseX, mouseY, partialTick);
            }
        }.primary());
        transport.add(new YmButton(Component.literal("■"), button -> send(Action.STOP, 0, "")))
                .setTooltip(Component.translatable("gui.ymtechnicals.creeper_blaster.stop"));
        transport.add(new YmButton(Component.literal("10 »"), button -> seekBy(10)))
                .setTooltip(Component.translatable("gui.ymtechnicals.creeper_blaster.forward"));

        boolean loop = initial != null && initial.loop();
        left.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper_blaster.loop"), LABEL_WIDTH,
                new YmSwitch(loop, on -> send(Action.LOOP, on ? 1 : 0, ""))));
        int volume = initial == null ? CreeperBlaster3000BlockEntity.DEFAULT_VOLUME : initial.volume();
        left.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper.volume"), LABEL_WIDTH,
                YmSlider.ofInt(Component.empty(), 0, 100, volume, value -> pendingVolume = value)
                        .setFormat(value -> Math.round(value) + " %")));
        int range = initial == null ? CreeperBlaster3000BlockEntity.DEFAULT_RANGE : initial.range();
        left.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper.range"), LABEL_WIDTH,
                YmSlider.ofInt(Component.empty(), 1, CreeperBlaster3000BlockEntity.MAX_RANGE, range,
                        value -> pendingRange = value).setFormat(value -> Math.round(value) + " m")));
        left.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper.code"), LABEL_WIDTH,
                new CreeperWidgets.CodeSlots(() -> {
                    CreeperBlaster3000BlockEntity blaster = blaster();
                    return blaster == null ? 0 : blaster.code();
                }, code -> send(Action.CODE, code, ""))));
        left.add(new YmText(Component.translatable("gui.ymtechnicals.creeper_blaster.code_help")).wrap().dim());
    }

    private void buildLibrary(YmContainer right) {
        right.setLayout(panel -> {
            List<YmElement> children = panel.children();
            // search, list, limits, link row, status
            int y = 0;
            int bottom = 12 + YmButton.DEFAULT_HEIGHT + 4 + 20;
            children.get(0).setBounds(0, y, panel.contentWidth(), 18);
            y += 22;
            children.get(1).setBounds(0, y, panel.contentWidth(), Math.max(20, panel.contentHeight() - y - bottom - 4));
            children.get(2).setBounds(0, panel.contentHeight() - bottom, panel.contentWidth(), 10);
            children.get(3).setBounds(0, panel.contentHeight() - bottom + 12, panel.contentWidth(),
                    YmButton.DEFAULT_HEIGHT);
            children.get(4).setBounds(0, panel.contentHeight() - 18, panel.contentWidth(), 18);
        });
        YmTextField searchField = right.add(new YmTextField(search, text -> search = text));
        searchField.setHint(Component.translatable("gui.ymtechnicals.creeper_blaster.search"));

        list = right.add(new YmScrollBox());
        list.setBackground(YmContainer.Background.INSET);
        list.setPadding(3, 3, YmScrollBox.BAR_WIDTH + 4, 3);
        list.setLayout(YmLayout.column(1));
        fillList();
        right.add(new YmText(this::limitsText).dim());

        YmContainer linkRow = right.add(new YmContainer());
        YmTextField linkField = linkRow.add(new YmTextField(link, text -> link = text));
        linkField.setMaxLength(2000);
        linkField.setHint(Component.translatable("gui.ymtechnicals.creeper_blaster.link"));
        linkField.setOnEnter(text -> download());
        YmButton downloadButton = linkRow.add(new YmButton(
                Component.translatable("gui.ymtechnicals.creeper_blaster.download"), button -> download()) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                               float partialTick) {
                CreeperClientState.Status status = CreeperClientState.status(pos);
                // Also while something runs: further links are queued.
                setEnabled(CreeperClientState.libraryInfo().mayDownload());
                super.render(graphics, mouseX, mouseY, partialTick);
            }
        });
        // Local files: system file dialog (or drop them onto the window).
        YmButton fileButton = linkRow.add(new YmButton(
                Component.translatable("gui.ymtechnicals.creeper_blaster.file"),
                button -> CreeperUploads.chooseFile(pos,
                        Component.translatable("gui.ymtechnicals.creeper_blaster.file.title").getString())) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                               float partialTick) {
                super.render(graphics, mouseX, mouseY, partialTick);
            }
        });
        fileButton.setTooltip(Component.translatable("gui.ymtechnicals.creeper_blaster.file.tooltip"));
        linkRow.setLayout(panel -> {
            boolean uploads = CreeperClientState.libraryInfo().maxUploadBytes() >= 0;
            fileButton.setVisible(uploads);
            int buttonWidth = Math.max(60, font.width(downloadButton.getText()) + 12);
            int fileWidth = uploads ? Math.max(44, font.width(fileButton.getText()) + 12) : 0;
            int end = panel.contentWidth();
            if (uploads) {
                fileButton.setBounds(end - fileWidth, 0, fileWidth, YmButton.DEFAULT_HEIGHT);
                end -= fileWidth + 3;
            }
            downloadButton.setBounds(end - buttonWidth, 0, buttonWidth, YmButton.DEFAULT_HEIGHT);
            linkField.setBounds(0, 1, end - buttonWidth - 3, 18);
        });
        right.add(new CreeperWidgets.StatusLine(pos));
    }

    /** (Re)fills the song list from the library and the search text. */
    private void fillList() {
        for (YmElement child : new ArrayList<>(list.children())) {
            list.remove(child);
        }
        builtLibraryVersion = CreeperClientState.libraryVersion();
        builtSearch = search;
        String query = search.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (SongInfo song : CreeperClientState.library()) {
            if (!query.isEmpty() && !(song.title() + " " + song.artist() + " " + song.addedBy())
                    .toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            list.add(new CreeperWidgets.SongRow(song, () -> {
                CreeperBlaster3000BlockEntity blaster = blaster();
                return blaster != null && blaster.songId().equals(song.id());
            }, selected -> send(Action.SELECT, 0, selected.id()), this::confirmDelete, this::songMenu))
                    .setSize(0, CreeperWidgets.SongRow.HEIGHT);
            shown++;
        }
        if (shown == 0) {
            list.add(new YmText(Component.translatable(CreeperClientState.library().isEmpty()
                    ? "gui.ymtechnicals.creeper_blaster.library_empty"
                    : "gui.ymtechnicals.creeper_blaster.nothing_found")).wrap().dim());
        }
    }

    /** Right click on a song: select, download again in the current quality, delete. */
    private void songMenu(SongInfo song, YmActionMenu menu) {
        menu.entry(Component.translatable("gui.ymtechnicals.creeper_blaster.menu.select"),
                () -> send(Action.SELECT, 0, song.id()));
        menu.entry(Component.translatable(song.redownloadable() ? "gui.ymtechnicals.creeper_blaster.menu.redownload"
                        : "gui.ymtechnicals.creeper_blaster.menu.redownload_unavailable"),
                song.redownloadable(), false, () -> send(Action.REDOWNLOAD, 0, song.id()));
        menu.separator();
        menu.entry(Component.translatable("gui.ymtechnicals.creeper_blaster.delete"), song.deletable(), false,
                () -> confirmDelete(song));
    }

    /** Audio files dropped onto the screen are uploaded one after the other. */
    @Override
    public void onFilesDrop(List<Path> files) {
        files.forEach(file -> CreeperUploads.start(pos, file));
    }

    /** "Your songs: 120 / 1024 MB · today: 30 / 500 MB", or why downloads are not possible. */
    private Component limitsText() {
        CreeperPayloads.Library info = CreeperClientState.libraryInfo();
        if (!info.mayDownload()) {
            return Component.translatable("gui.ymtechnicals.creeper_blaster.limits.no_download");
        }
        Component storage = Component.translatable("gui.ymtechnicals.creeper_blaster.limits.storage",
                megabytes(info.usedBytes()), info.quotaBytes() < 0 ? "∞" : megabytes(info.quotaBytes()));
        if (info.dailyLimitBytes() < 0) {
            return storage;
        }
        return Component.empty().append(storage).append("  ·  ").append(Component.translatable(
                "gui.ymtechnicals.creeper_blaster.limits.daily", megabytes(info.dailyBytes()),
                megabytes(info.dailyLimitBytes())));
    }

    private static String megabytes(long bytes) {
        return String.valueOf(Math.round(bytes / (1024.0 * 1024.0)));
    }

    private void confirmDelete(SongInfo song) {
        YmDialog.confirm(this, Component.translatable("gui.ymtechnicals.creeper_blaster.delete"),
                Component.translatable("gui.ymtechnicals.creeper_blaster.delete.message", song.title()),
                Component.translatable("gui.ymtechnicals.creeper_blaster.delete"),
                () -> send(Action.DELETE, 0, song.id()));
    }

    private void download() {
        if (!link.isBlank()) {
            send(Action.DOWNLOAD, 0, link);
        }
    }

    private ChannelState.Mode mode() {
        CreeperBlaster3000BlockEntity blaster = blaster();
        return blaster == null ? ChannelState.Mode.STOPPED : blaster.mode();
    }

    private void seekBy(int seconds) {
        CreeperBlaster3000BlockEntity blaster = blaster();
        if (blaster != null) {
            send(Action.SEEK, (int) (blaster.positionTicks(gameTime()) / 20) + seconds, "");
        }
    }

    @Override
    public void tick() {
        super.tick();
        CreeperBlaster3000BlockEntity blaster = blaster();
        Minecraft minecraft = Minecraft.getInstance();
        if (blaster == null || minecraft.player == null
                || minecraft.player.distanceToSqr(pos.getCenter()) > 10.0 * 10.0) {
            onClose();
            return;
        }
        if (builtLibraryVersion != CreeperClientState.libraryVersion() || !builtSearch.equals(search)) {
            fillList();
            relayout();
        }
        long now = Util.getMillis();
        if (now - lastSend >= SEND_INTERVAL_MILLIS) {
            if (pendingVolume >= 0) {
                send(Action.VOLUME, pendingVolume, "");
                pendingVolume = -1;
                lastSend = now;
            }
            if (pendingRange >= 0) {
                send(Action.RANGE, pendingRange, "");
                pendingRange = -1;
                lastSend = now;
            }
        }
    }

    @Override
    public void removed() {
        if (pendingVolume >= 0) {
            send(Action.VOLUME, pendingVolume, "");
        }
        if (pendingRange >= 0) {
            send(Action.RANGE, pendingRange, "");
        }
        if (!link.equals(blaster() == null ? link : blaster().link())) {
            send(Action.LINK, 0, link);
        }
        send(Action.CLOSE, 0, "");
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
