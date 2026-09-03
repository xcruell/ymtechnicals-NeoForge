package com.yesmenn.technicals.client;

import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.client.screen.ObserversEyeScreen;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "ymtechnicals", value = Dist.CLIENT)
public class YMTechnicalsClient {

    public YMTechnicalsClient(ModContainer modContainer) {
//        NeoForge.EVENT_BUS.addListener(Soundeffectz3000ClientHooks::onClientTick);
//        NeoForge.EVENT_BUS.addListener(Soundeffectz3000ClientHooks::onClientLoggingOut);

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                ConfigurationScreen::new
        );
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Client-side initialization code
        });
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // Payload registration for screen opening packets
        // This will be handled by the network system
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.OBSERVERS_EYE.get(), ObserversEyeRenderer::new);

    }
}