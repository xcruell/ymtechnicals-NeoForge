package com.yesmenn.technicals.client;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge client entrypoint: client services and the client events of the common code. */
@Mod(value = YMTechnicals.MODID, dist = Dist.CLIENT)
public final class YMTechnicalsNeoForgeClient {
    public YMTechnicalsNeoForgeClient(IEventBus modBus) {
        ClientPlatform.init(new ClientPlatform.Impl() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                PacketDistributor.sendToServer(payload);
            }

            @Override
            public BakedModel getAdditionalModel(ResourceLocation id) {
                return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id));
            }
        });

        modBus.addListener((FMLClientSetupEvent event) -> TechnicalsClient.init());
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                TechnicalsClient.registerRenderers(event::registerBlockEntityRenderer));
        modBus.addListener((ModelEvent.RegisterAdditional event) -> TechnicalsClient.ADDITIONAL_MODELS
                .forEach(id -> event.register(ModelResourceLocation.standalone(id))));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> TechnicalsClient.tick());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) ->
                TechnicalsClient.screenOpened(event.getScreen()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> TechnicalsClient.loggedIn());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TechnicalsClient.loggedOut());
    }
}
