package com.yesmenn.technicals.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;
import com.yesmenn.technicals.client.screen.ObserversEyeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public class ObserversEyeRenderer implements BlockEntityRenderer<ObserversEyeBlockEntity> {
    private static final int CYLINDER_SEGMENTS = 48;
    private static final float ALPHA = 0.9F;

    public ObserversEyeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ObserversEyeBlockEntity sensor, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        PreviewData preview = previewData(sensor);
        if (preview == null) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        AABB worldBox = preview.box();
        AABB box = worldBox.move(
                -sensor.getBlockPos().getX(),
                -sensor.getBlockPos().getY(),
                -sensor.getBlockPos().getZ());

        if (preview.cylinder()) {
            renderCylinder(poseStack, consumer, box);
        } else {
            renderBox(poseStack, consumer, box);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ObserversEyeBlockEntity blockEntity) {
        return previewData(blockEntity) != null;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, AABB box) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        line(poseStack, consumer, x0, y0, z0, x1, y0, z0, 1.0F, 0.25F, 0.25F);
        line(poseStack, consumer, x1, y0, z1, x0, y0, z1, 1.0F, 0.25F, 0.25F);
        line(poseStack, consumer, x0, y1, z0, x1, y1, z0, 1.0F, 0.25F, 0.25F);
        line(poseStack, consumer, x1, y1, z1, x0, y1, z1, 1.0F, 0.25F, 0.25F);
        line(poseStack, consumer, x1, y0, z0, x1, y0, z1, 0.25F, 0.45F, 1.0F);
        line(poseStack, consumer, x0, y0, z1, x0, y0, z0, 0.25F, 0.45F, 1.0F);
        line(poseStack, consumer, x1, y1, z0, x1, y1, z1, 0.25F, 0.45F, 1.0F);
        line(poseStack, consumer, x0, y1, z1, x0, y1, z0, 0.25F, 0.45F, 1.0F);
        line(poseStack, consumer, x0, y0, z0, x0, y1, z0, 0.3F, 1.0F, 0.35F);
        line(poseStack, consumer, x1, y0, z0, x1, y1, z0, 0.3F, 1.0F, 0.35F);
        line(poseStack, consumer, x1, y0, z1, x1, y1, z1, 0.3F, 1.0F, 0.35F);
        line(poseStack, consumer, x0, y0, z1, x0, y1, z1, 0.3F, 1.0F, 0.35F);
    }

    private static void renderCylinder(PoseStack poseStack, VertexConsumer consumer, AABB box) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double radiusX = (box.maxX - box.minX) * 0.5D;
        double radiusZ = (box.maxZ - box.minZ) * 0.5D;
        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / CYLINDER_SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / CYLINDER_SEGMENTS;
            double x0 = centerX + Math.cos(a0) * radiusX;
            double z0 = centerZ + Math.sin(a0) * radiusZ;
            double x1 = centerX + Math.cos(a1) * radiusX;
            double z1 = centerZ + Math.sin(a1) * radiusZ;
            float red = Math.abs(Math.cos(a0)) > Math.abs(Math.sin(a0)) ? 1.0F : 0.25F;
            float blue = Math.abs(Math.sin(a0)) >= Math.abs(Math.cos(a0)) ? 1.0F : 0.35F;
            line(poseStack, consumer, x0, box.minY, z0, x1, box.minY, z1, red, 0.35F, blue);
            line(poseStack, consumer, x0, box.maxY, z0, x1, box.maxY, z1, red, 0.35F, blue);
            if (i % 12 == 0) {
                line(poseStack, consumer, x0, box.minY, z0, x0, box.maxY, z0, 0.3F, 1.0F, 0.35F);
            }
        }
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             float red, float green, float blue) {
        PoseStack.Pose pose = poseStack.last();
        float nx = (float) (x1 - x0);
        float ny = (float) (y1 - y0);
        float nz = (float) (z1 - z0);
        consumer.addVertex(pose, (float) x0, (float) y0, (float) z0)
                .setColor(red, green, blue, ALPHA)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, ALPHA)
                .setNormal(pose, nx, ny, nz);
    }

    private static PreviewData previewData(ObserversEyeBlockEntity sensor) {
        if (Minecraft.getInstance().screen instanceof ObserversEyeScreen screen && screen.edits(sensor.getBlockPos())) {
            return screen.previewEnabled()
                    ? new PreviewData(screen.previewDetectionBox(), screen.previewCylinder())
                    : null;
        }
        return sensor.isPreview()
                ? new PreviewData(sensor.detectionBox(), sensor.isCylinder())
                : null;
    }

    private record PreviewData(AABB box, boolean cylinder) {
    }
}
