package com.yesmenn.technicals.client;

import com.yesmenn.technicals.platform.ClientPlatform;
import com.yesmenn.technicals.registry.ModNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Fabric client entrypoint: client services, packets, renderers and the client events of the common code. */
public final class YMTechnicalsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.init(new ClientPlatform.Impl() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                ClientPlayNetworking.send(payload);
            }

            @Override
            public BakedModel getAdditionalModel(ResourceLocation id) {
                return Minecraft.getInstance().getModelManager().getModel(id);
            }
        });
        TechnicalsClient.init();
        ModNetworking.toClient().forEach(YMTechnicalsFabricClient::registerReceiver);

        TechnicalsClient.registerRenderers(BlockEntityRenderers::register);
        ModelLoadingPlugin.register(context -> context.addModels(TechnicalsClient.ADDITIONAL_MODELS));
        // NeoForge reads "render_type" from the block models; Fabric ignores it, so the layers are set here.
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(),
                TechnicalsClient.cutoutBlocks().toArray(Block[]::new));

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> TechnicalsClient.tick());
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
                TechnicalsClient.screenOpened(screen));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> TechnicalsClient.loggedIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> TechnicalsClient.loggedOut());
    }

    private static <T extends CustomPacketPayload> void registerReceiver(ModNetworking.ToClient<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> entry.handler().accept(payload));
    }
}
