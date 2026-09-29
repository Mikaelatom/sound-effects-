package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.ShikigamiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a shikigami as the block it was made from, hopping and leaning as it moves, with a talisman stuck to its
 * front. Flashes red when hurt, like any mob.
 */
public class ShikigamiRenderer extends EntityRenderer<ShikigamiEntity> {
    static final ResourceLocation TALISMAN = TensuraFragments.id("textures/entity/talisman.png");

    public ShikigamiRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.45F;
    }

    @Override
    public void render(ShikigamiEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float walk = entity.walkAnimation.position(partialTick);
        float walkSpeed = Math.min(1, entity.walkAnimation.speed(partialTick));
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);

        poseStack.pushPose();
        // Hop while walking, float gently while idle.
        float hop = Math.abs(Mth.sin(walk * 0.6F)) * 0.25F * walkSpeed + Mth.sin(age * 0.1F) * 0.03F;
        poseStack.translate(0, hop, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(walk * 0.6F) * 8 * walkSpeed));
        if (entity.deathTime > 0) {
            float shrink = 1 - Math.min(1, (entity.deathTime + partialTick) / 20F);
            poseStack.scale(shrink, shrink, shrink);
        }

        float size = 0.85F;
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(entity.getBlock(), poseStack, buffers, light,
                LivingEntityRenderer.getOverlayCoords(entity, 0));
        poseStack.popPose();

        // The talisman on its front face.
        poseStack.translate(0, size * 0.5F, size * 0.5F + 0.01F);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TALISMAN));
        float w = 0.12F;
        float h = 0.26F;
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        consumer.addVertex(pose, -w, -h, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, w, -h, 0).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, w, h, 0).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, -w, h, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShikigamiEntity entity) {
        return TALISMAN;
    }
}
