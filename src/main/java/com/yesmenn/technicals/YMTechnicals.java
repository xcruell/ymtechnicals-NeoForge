package com.yesmenn.technicals;

import com.yesmenn.technicals.registry.ModNetworking;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import com.yesmenn.technicals.registry.ModBlocks;
import com.yesmenn.technicals.registry.ModCreativeModeTabs;
import com.yesmenn.technicals.registry.ModItems;

@Mod(YMTechnicals.MODID)
public class YMTechnicals {
    public static final String MODID = "ymtechnicals";
    private static final Logger LOGGER = LogUtils.getLogger();

    public YMTechnicals(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetworking::register);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);

        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }

    public static void debug(ServerPlayer player, String message) {
        player.sendSystemMessage(
                Component.literal("[DEBUG] " + message)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("YMTechnicals - Blocks registered successfully!");
    }

}
