package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.yifa.SpiritElement;
import com.tensurafragments.yifa.WispEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A spirit: a soft glowing orb of its element, always facing you. Only drawn for those who can see it. */
public class WispRenderer extends EntityRenderer<WispEntity> {
    public WispRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(WispEntity wisp, Frustum frustum, double camX, double camY, double camZ) {
        return ClientSpiritSight.canSee(wisp) && super.shouldRender(wisp, frustum, camX, camY, camZ);
    }

    @Override
    public void render(WispEntity wisp, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        float time = wisp.tickCount + partialTick;
        float size = 0.45F + 0.06F * Mth.sin(time * 0.25F);
        poseStack.pushPose();
        poseStack.translate(0, wisp.getBbHeight() / 2, 0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(size, size, size);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(wisp)));
        int alpha = wisp.isWild() ? 200 : 255;
        vertex(consumer, pose, -0.5F, -0.5F, 0, 1, alpha);
        vertex(consumer, pose, 0.5F, -0.5F, 1, 1, alpha);
        vertex(consumer, pose, 0.5F, 0.5F, 1, 0, alpha);
        vertex(consumer, pose, -0.5F, 0.5F, 0, 0, alpha);
        poseStack.popPose();
        super.render(wisp, yaw, partialTick, poseStack, buffers, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int alpha) {
        consumer.addVertex(pose, x, y, 0).setColor(255, 255, 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(WispEntity wisp) {
        return texture(wisp.getElement());
    }

    static ResourceLocation texture(SpiritElement element) {
        return TensuraFragments.id("textures/entity/spirit/" + element.id() + ".png");
    }
}
