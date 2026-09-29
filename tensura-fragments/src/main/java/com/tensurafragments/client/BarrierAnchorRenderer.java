package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.BarrierAnchorEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws a standing talisman, and while the barrier is up, a glowing wall to the next anchor. */
public class BarrierAnchorRenderer extends EntityRenderer<BarrierAnchorEntity> {
    private static final ResourceLocation WALL = TensuraFragments.id("textures/entity/barrier_wall.png");

    public BarrierAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(BarrierAnchorEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // The wall reaches past this entity's tiny box, so don't frustum-cull it.
        return entity.distanceToSqr(camX, camY, camZ) < 128 * 128;
    }

    @Override
    public void render(BarrierAnchorEntity anchor, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = anchor.tickCount + partialTick;

        // The talisman: upright, slowly turning, bobbing a little.
        poseStack.pushPose();
        poseStack.translate(0, 0.35 + Mth.sin(age * 0.08F) * 0.04, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer paper = buffers.getBuffer(RenderType.entityCutoutNoCull(ShikigamiRenderer.TALISMAN));
        int glow = anchor.isActive() ? LightTexture.FULL_BRIGHT : light;
        quad(paper, pose, -0.14F, -0.3F, 0.14F, 0.3F, 0, glow, 255);
        poseStack.popPose();

        BarrierAnchorEntity next = anchor.isActive() ? anchor.getNext() : null;
        if (next != null) {
            Vec3 here = anchor.getPosition(partialTick);
            Vec3 to = next.getPosition(partialTick).subtract(here);
            float height = anchor.getWallHeight();
            float scroll = (age * 0.01F) % 1F;
            int alpha = (int) (110 + 40 * Mth.sin(age * 0.15F));
            float length = (float) to.horizontalDistance();
            VertexConsumer wall = buffers.getBuffer(RenderType.entityTranslucentEmissive(WALL));
            PoseStack.Pose p = poseStack.last();
            float u1 = Math.max(1, length / 2);
            float v1 = height / 2;
            wall.addVertex(p, 0, 0, 0).setColor(255, 230, 150, alpha).setUv(0, v1 + scroll)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            wall.addVertex(p, (float) to.x, (float) to.y, (float) to.z).setColor(255, 230, 150, alpha).setUv(u1, v1 + scroll)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            wall.addVertex(p, (float) to.x, (float) to.y + height, (float) to.z).setColor(255, 230, 150, 0).setUv(u1, scroll)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            wall.addVertex(p, 0, height, 0).setColor(255, 230, 150, 0).setUv(0, scroll)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
        }
        super.render(anchor, yaw, partialTick, poseStack, buffers, light);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                             int light, int alpha) {
        consumer.addVertex(pose, x0, y0, z).setColor(255, 255, 255, alpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, x1, y0, z).setColor(255, 255, 255, alpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, x1, y1, z).setColor(255, 255, 255, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, x0, y1, z).setColor(255, 255, 255, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(BarrierAnchorEntity entity) {
        return WALL;
    }
}
