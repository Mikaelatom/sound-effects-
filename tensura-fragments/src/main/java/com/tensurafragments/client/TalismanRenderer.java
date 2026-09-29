package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.shikigami.TalismanEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws a thrown talisman facing the camera and spinning, in its spell's colours. Leaf talismans are tinted green. */
public class TalismanRenderer extends EntityRenderer<TalismanEntity> {
    public TalismanRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static ResourceLocation texture(Spell spell) {
        return TensuraFragments.id("textures/entity/talisman_" + spell.id() + ".png");
    }

    @Override
    public void render(TalismanEntity talisman, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees((talisman.tickCount + partialTick) * 30));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(talisman.getSpell())));
        int colour = talisman.isLeaf() ? 0xFF96FF78 : 0xFFFFFFFF;
        float w = 0.15F;
        float h = 0.3F;
        int glow = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose, -w, -h, 0).setColor(colour).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(glow).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, w, -h, 0).setColor(colour).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(glow).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, w, h, 0).setColor(colour).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(glow).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, -w, h, 0).setColor(colour).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(glow).setNormal(pose, 0, 0, 1);
        poseStack.popPose();
        super.render(talisman, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TalismanEntity talisman) {
        return texture(talisman.getSpell());
    }
}
