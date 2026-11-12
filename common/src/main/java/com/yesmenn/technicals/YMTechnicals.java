package com.yesmenn.technicals;

import com.mojang.logging.LogUtils;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import com.yesmenn.technicals.registry.ModBlocks;
import com.yesmenn.technicals.registry.ModItems;
import org.slf4j.Logger;

import java.util.List;

/** Loader-independent mod setup; called by the NeoForge and Fabric entrypoints after {@code Platform.init}. */
public final class YMTechnicals {
    public static final String MODID = "ymtechnicals";
    public static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Came from the YesMenn mod: worlds from back then keep them (NeoForge maps yesmenn:name to ymtechnicals:name when
     * that mod is not installed any more; it only ever ran on NeoForge).
     */
    public static final List<String> FROM_YESMENN = List.of("creeper_blaster_3000", "creeper_speaker_3000",
            "code_sheet");

    private YMTechnicals() {
    }

    public static void init() {
        // Blocks first: items and block entity types use them (Fabric registers at once).
        ModBlocks.init();
        ModItems.init();
        ModBlockEntityTypes.init();
    }
}
