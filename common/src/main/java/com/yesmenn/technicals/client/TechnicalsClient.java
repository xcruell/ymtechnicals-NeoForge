package com.yesmenn.technicals.client;

import com.yesmenn.technicals.creeper.block.CreeperBlocks;
import com.yesmenn.technicals.creeper.client.CreeperAudio;
import com.yesmenn.technicals.creeper.client.CreeperClientState;
import com.yesmenn.technicals.creeper.client.CreeperGlowRenderer;
import com.yesmenn.technicals.creeper.client.CreeperUploads;
import com.yesmenn.technicals.creeper.client.SongCache;
import com.yesmenn.technicals.creeper.client.screen.CreeperBlasterScreen;
import com.yesmenn.technicals.creeper.client.screen.CreeperSpeakerScreen;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import com.yesmenn.technicals.registry.ModBlocks;
import com.yesmenn.technicals.client.screen.ToolsSetupScreen;
import com.yesmenn.technicals.creeper.ToolsConfig;
import com.yesmenn.technicals.creeper.server.AudioTools;
import com.yesmenn.technicals.creeper.server.CreeperWorker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/** Loader-independent client setup; the loaders call these from their client events. */
public final class TechnicalsClient {
    /** Standalone models (glow overlays), registered by the loaders' model events. */
    public static final List<ResourceLocation> ADDITIONAL_MODELS = List.of(CreeperGlowRenderer.BLASTER_GLOW,
            CreeperGlowRenderer.SPEAKER_GLOW);

    private TechnicalsClient() {
    }

    @FunctionalInterface
    public interface RendererRegistrar {
        <T extends BlockEntity> void register(BlockEntityType<? extends T> type,
                                              BlockEntityRendererProvider<T> provider);
    }

    public static void init() {
        CreeperBlocks.openBlasterScreen = pos -> Minecraft.getInstance().setScreen(new CreeperBlasterScreen(pos));
        CreeperBlocks.openSpeakerScreen = pos -> Minecraft.getInstance().setScreen(new CreeperSpeakerScreen(pos));
    }

    public static void registerRenderers(RendererRegistrar registrar) {
        registrar.register(ModBlockEntityTypes.OBSERVERS_EYE.get(), ObserversEyeRenderer::new);
        registrar.register(ModBlockEntityTypes.CREEPER_BLASTER_3000.get(), CreeperGlowRenderer.Blaster::new);
        registrar.register(ModBlockEntityTypes.CREEPER_SPEAKER_3000.get(), CreeperGlowRenderer.Speaker::new);
    }

    /** Blocks drawn with cutout; NeoForge reads it from the models ("render_type"), Fabric needs this list. */
    public static List<Block> cutoutBlocks() {
        return List.of(ModBlocks.CREEPER_BLASTER_3000.get(), ModBlocks.CREEPER_SPEAKER_3000.get());
    }

    /** End of a client tick. */
    private static int ticks;

    public static void tick() {
        CreeperAudio.tick();
        CreeperUploads.tick();
        if (++ticks % 20 == 0) {
            SongCache.sweep();
        }
        Soundeffectz3000ClientHooks.tick();
    }

    private static boolean titleSeen;

    /**
     * A screen finished its init. The first main menu asks once whether yt-dlp/ffmpeg may be downloaded; when that is
     * settled it installs / updates them in the background right away (not only when a world is opened).
     */
    public static void screenOpened(Screen screen) {
        if (titleSeen || !(screen instanceof TitleScreen)) {
            return;
        }
        titleSeen = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (!ToolsConfig.get().confirmed) {
            minecraft.execute(() -> minecraft.setScreen(new ToolsSetupScreen(screen)));
        } else {
            CreeperWorker.download(() -> {
                AudioTools.prepare();
                return null;
            });
        }
    }

    public static void loggedIn() {
        SongCache.scan();
    }

    public static void loggedOut() {
        CreeperAudio.reset();
        CreeperClientState.reset();
        SongCache.reset();
        CreeperUploads.reset();
        Soundeffectz3000ClientHooks.loggedOut();
    }
}
