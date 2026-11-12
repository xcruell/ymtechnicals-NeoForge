package com.yesmenn.technicals.client.screen;

import com.yesmenn.ducttape.client.kit.YmContainer;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.YmScrollBox;
import com.yesmenn.ducttape.client.kit.element.YmButton;
import com.yesmenn.ducttape.client.kit.element.YmSwitch;
import com.yesmenn.ducttape.client.kit.element.YmText;
import com.yesmenn.ducttape.client.kit.element.YmWindow;
import com.yesmenn.ducttape.client.kit.layout.YmLayout;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.technicals.creeper.ToolsConfig;
import com.yesmenn.technicals.creeper.server.AudioTools;
import com.yesmenn.technicals.creeper.server.CreeperWorker;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Asked once in the main menu: may YM Technicals download yt-dlp and ffmpeg (each with what it is and what it is
 * for) and keep yt-dlp up to date? Switched off, the text says where to put the program instead. Nothing is
 * downloaded before this is confirmed. Applies to single player and LAN worlds; servers have their own file.
 */
public class ToolsSetupScreen extends YmScreen {
    private boolean installYtDlp;
    private boolean installFfmpeg;
    private boolean autoUpdate;

    public ToolsSetupScreen(@Nullable Screen parent) {
        super(tr("title"), parent);
        ToolsConfig config = ToolsConfig.get();
        installYtDlp = config.installYtDlp;
        installFfmpeg = config.installFfmpeg;
        autoUpdate = config.autoUpdateYtDlp;
        setAccent(YmStyle.ACCENT_TECHNICALS);
    }

    @Override
    protected void build(YmContainer root) {
        int w = Math.min(380, width - 16);
        int h = Math.min(300, height - 16);
        YmWindow window = root.add(new YmWindow(getTitle(), w, h));
        window.centerIn(root);
        window.footer().add(new YmButton(tr("confirm"), button -> confirm()).primary());

        YmScrollBox page = window.content().add(new YmScrollBox());
        window.content().setPadding(6).setLayout(YmLayout.fill());
        page.setPadding(0, 0, YmScrollBox.BAR_WIDTH + 3, 0).setLayout(YmLayout.column(4));

        page.add(new YmText(tr("intro")).wrap());
        // Before anything is downloaded, the PATH and the tools folder are searched first.
        page.add(new YmText(tr("path_check")).wrap().dim());
        page.add(new YmText(Component.empty()));
        page.add(new YmSwitch(tr("yt_dlp"), installYtDlp, on -> installYtDlp = on));
        page.add(new YmText(() -> installYtDlp ? tr("yt_dlp.info")
                : tr("manual", AudioTools.ytDlpName(), AudioTools.toolsFolder().toString())).wrap().dim());
        page.add(new YmSwitch(tr("ffmpeg"), installFfmpeg, on -> installFfmpeg = on));
        page.add(new YmText(() -> installFfmpeg ? tr("ffmpeg.info")
                : tr("manual", AudioTools.exe("ffmpeg") + " + " + AudioTools.exe("ffprobe"),
                AudioTools.ffmpegFolder().toString())).wrap().dim());
        page.add(new YmSwitch(tr("update"), autoUpdate, on -> autoUpdate = on));
        page.add(new YmText(tr("update.info")).wrap().dim());
        page.add(new YmText(Component.empty()));
        page.add(new YmText(tr("later")).wrap().dim());
    }

    private void confirm() {
        ToolsConfig.save(installYtDlp, installFfmpeg, autoUpdate);
        // Fetches what is allowed now in the background, so the first song download does not wait for it.
        CreeperWorker.download(() -> {
            AudioTools.prepare();
            return null;
        });
        onClose();
    }

    /** Only the button closes it: the choice should be a conscious one. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.ymtechnicals.tools_setup." + key, args);
    }
}
