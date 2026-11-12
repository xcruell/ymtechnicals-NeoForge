package com.yesmenn.technicals.creeper.client.screen;

import com.yesmenn.ducttape.client.kit.YmContainer;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.element.YmButton;
import com.yesmenn.ducttape.client.kit.element.YmFormRow;
import com.yesmenn.ducttape.client.kit.element.YmSlider;
import com.yesmenn.ducttape.client.kit.element.YmText;
import com.yesmenn.ducttape.client.kit.element.YmWindow;
import com.yesmenn.ducttape.client.kit.layout.YmLayout;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import com.yesmenn.technicals.creeper.client.CreeperClientState;
import com.yesmenn.technicals.creeper.network.ChannelState;
import com.yesmenn.technicals.creeper.network.CreeperPayloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.yesmenn.technicals.platform.ClientPlatform;
import org.jetbrains.annotations.Nullable;

/** CreeperSpeaker3000 screen (YM kit): what its channel plays, its code, volume and range. */
public class CreeperSpeakerScreen extends YmScreen {
    private static final int LABEL_WIDTH = 52;
    private static final long SEND_INTERVAL_MILLIS = 120;

    private final BlockPos pos;
    private int code;
    private int volume;
    private int range;
    private boolean dirty;
    private long lastSend;

    public CreeperSpeakerScreen(BlockPos pos) {
        super(Component.translatable("block.ymtechnicals.creeper_speaker_3000"), null);
        this.pos = pos.immutable();
        CreeperSpeaker3000BlockEntity speaker = speaker();
        code = speaker == null ? 0 : speaker.code();
        volume = speaker == null ? CreeperBlaster3000BlockEntity.DEFAULT_VOLUME : speaker.volume();
        range = speaker == null ? CreeperBlaster3000BlockEntity.DEFAULT_RANGE : speaker.range();
        setAccent(YmStyle.ACCENT_TECHNICALS);
    }

    @Nullable
    private CreeperSpeaker3000BlockEntity speaker() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof CreeperSpeaker3000BlockEntity
                speaker ? speaker : null;
    }

    @Nullable
    private ChannelState channel() {
        return CreeperClientState.channel(code);
    }

    private long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    @Override
    protected void build(YmContainer root) {
        int w = Math.min(250, width - 16);
        int h = Math.min(200, height - 16);
        YmWindow window = root.add(new YmWindow(getTitle(), w, h)).closable();
        window.centerIn(root);
        window.footer().add(new YmButton(Component.translatable("gui.done"), button -> onClose()).primary());

        YmContainer content = window.content();
        content.setPadding(5).setLayout(YmLayout.column(4));
        content.add(new YmText(() -> {
            ChannelState channel = channel();
            if (channel == null) {
                return Component.translatable("gui.ymtechnicals.creeper_speaker.no_blaster");
            }
            return channel.songId().isEmpty() ? Component.translatable("gui.ymtechnicals.creeper_blaster.no_song")
                    : Component.literal(channel.title());
        }));
        content.add(new YmText(() -> {
            ChannelState channel = channel();
            if (channel == null) {
                return Component.translatable("gui.ymtechnicals.creeper_speaker.no_blaster.hint");
            }
            return Component.translatable(switch (channel.mode()) {
                case PLAYING -> "gui.ymtechnicals.creeper_blaster.playing";
                case PAUSED -> "gui.ymtechnicals.creeper_blaster.paused";
                default -> "gui.ymtechnicals.creeper_blaster.stopped";
            });
        }).dim());
        CreeperWidgets.SeekBar progress = content.add(new CreeperWidgets.SeekBar(
                () -> {
                    ChannelState channel = channel();
                    return channel == null ? 0 : channel.positionTicks(gameTime());
                },
                () -> {
                    ChannelState channel = channel();
                    return channel == null ? 0 : channel.songTicks();
                },
                seconds -> {
                }));
        // Only the blaster can seek.
        progress.setEnabled(false);

        content.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper.code"), LABEL_WIDTH,
                new CreeperWidgets.CodeSlots(() -> code, value -> {
                    code = value;
                    dirty = true;
                })));
        content.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper.volume"), LABEL_WIDTH,
                YmSlider.ofInt(Component.empty(), 0, 100, volume, value -> {
                    volume = value;
                    dirty = true;
                }).setFormat(value -> Math.round(value) + " %")));
        content.add(new YmFormRow(Component.translatable("gui.ymtechnicals.creeper.range"), LABEL_WIDTH,
                YmSlider.ofInt(Component.empty(), 1, CreeperBlaster3000BlockEntity.MAX_RANGE, range, value -> {
                    range = value;
                    dirty = true;
                }).setFormat(value -> Math.round(value) + " m")));
        content.add(new YmText(Component.translatable("gui.ymtechnicals.creeper_speaker.help")).wrap().dim());
    }

    private void sendSettings() {
        ClientPlatform.sendToServer(new CreeperPayloads.SpeakerSettings(pos, code, range, volume));
        dirty = false;
        lastSend = Util.getMillis();
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (speaker() == null || minecraft.player == null
                || minecraft.player.distanceToSqr(pos.getCenter()) > 10.0 * 10.0) {
            onClose();
            return;
        }
        if (dirty && Util.getMillis() - lastSend >= SEND_INTERVAL_MILLIS) {
            sendSettings();
        }
    }

    @Override
    public void removed() {
        if (dirty) {
            sendSettings();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
