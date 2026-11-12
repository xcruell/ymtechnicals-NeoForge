package com.yesmenn.technicals.creeper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000Block;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000Block;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import com.yesmenn.technicals.platform.ClientPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Glowing parts of the Creeper blocks while they play: an extra full-bright model drawn slightly larger over the block.
 * Nothing is drawn while they are silent.
 */
public abstract class CreeperGlowRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    /** Standalone models, registered by the loaders (see {@code TechnicalsClient#ADDITIONAL_MODELS}). */
    public static final ResourceLocation BLASTER_GLOW = model("block/creeper_blaster_3000_emissive");
    public static final ResourceLocation SPEAKER_GLOW = model("block/creeperspeaker3000_full_emissive");

    private static ResourceLocation model(String path) {
        return ResourceLocation.fromNamespaceAndPath(YMTechnicals.MODID, path);
    }

    /** True while the block plays. */
    protected abstract boolean glows(T blockEntity);

    protected abstract ResourceLocation glowModel();

    protected abstract float yRotation(BlockState state);

    protected boolean upsideDown(BlockState state) {
        return false;
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!glows(blockEntity)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = ClientPlatform.getAdditionalModel(glowModel());
        if (model == minecraft.getModelManager().getMissingModel()) {
            return;
        }
        BlockState state = blockEntity.getBlockState();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        if (upsideDown(state)) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotation(state)));
        // A hair larger than the block, so the glow does not flicker against its faces.
        poseStack.scale(1.002F, 1.002F, 1.002F);
        poseStack.translate(-0.5, -0.5, -0.5);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(poseStack.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model, 1.0F, 1.0F, 1.0F,
                LightTexture.FULL_BRIGHT, packedOverlay);
        poseStack.popPose();
    }

    private static float facingRotation(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    public static final class Blaster extends CreeperGlowRenderer<CreeperBlaster3000BlockEntity> {
        public Blaster(BlockEntityRendererProvider.Context context) {
        }

        @Override
        protected boolean glows(CreeperBlaster3000BlockEntity blaster) {
            return blaster.isPlaying();
        }

        @Override
        protected ResourceLocation glowModel() {
            return BLASTER_GLOW;
        }

        @Override
        protected float yRotation(BlockState state) {
            return facingRotation(state.getValue(CreeperBlaster3000Block.FACING));
        }
    }

    public static final class Speaker extends CreeperGlowRenderer<CreeperSpeaker3000BlockEntity> {
        public Speaker(BlockEntityRendererProvider.Context context) {
        }

        @Override
        protected boolean glows(CreeperSpeaker3000BlockEntity speaker) {
            // Only the full speaker has a glowing front.
            return speaker.getBlockState().getValue(CreeperSpeaker3000Block.FULL)
                    && CreeperClientState.isPlaying(speaker.code());
        }

        @Override
        protected ResourceLocation glowModel() {
            return SPEAKER_GLOW;
        }

        @Override
        protected boolean upsideDown(BlockState state) {
            return state.getValue(CreeperSpeaker3000Block.UPSIDE_DOWN);
        }

        @Override
        protected float yRotation(BlockState state) {
            float rotation = facingRotation(state.getValue(CreeperSpeaker3000Block.FACING));
            // Turned upside down, north and south swap.
            return upsideDown(state) && (rotation == 0.0F || rotation == 180.0F) ? 180.0F - rotation : rotation;
        }
    }
}
