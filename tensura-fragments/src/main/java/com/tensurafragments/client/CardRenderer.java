package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.card.CardEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the card as a flat quad: spinning while it flies, flat against the surface once it sticks. */
public class CardRenderer extends EntityRenderer<CardEntity> {
    private static final ResourceLocation TEXTURE = TensuraFragments.id("textures/entity/card.png");
    private static final float HALF_WIDTH = 0.2F;
    private static final float HALF_HEIGHT = 0.3F;

    public CardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CardEntity card, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        int ticksLeft = card.getTicksLeft();
        // Blink during the last three seconds so the player knows it is about to fizzle.
        if (ticksLeft < 60 && (card.tickCount / (ticksLeft < 20 ? 2 : 4)) % 2 == 0) {
            super.render(card, yaw, partialTick, poseStack, buffers, light);
            return;
        }

        poseStack.pushPose();
        float age = card.tickCount + partialTick;
        if (!card.isStuck()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, card.yRotO, card.getYRot())));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * 45F));
        } else if (card.isStuckToEntity()) {
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        } else {
            poseStack.mulPose(card.getFace().getRotation());
        }

        // Charge tints the card from white to a hot magenta and makes it glow when full.
        float charge = Mth.clamp(card.getCharge() - 0.5F, 0, 1);
        int red = 255;
        int green = (int) (255 - 150 * charge);
        int blue = (int) (255 - 60 * charge);
        int packedLight = card.isFullyCharged() ? LightTexture.FULL_BRIGHT : light;

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, -HALF_WIDTH, -HALF_HEIGHT, 0, 1, red, green, blue, packedLight);
        vertex(consumer, pose, -HALF_WIDTH, HALF_HEIGHT, 0, 0, red, green, blue, packedLight);
        vertex(consumer, pose, HALF_WIDTH, HALF_HEIGHT, 1, 0, red, green, blue, packedLight);
        vertex(consumer, pose, HALF_WIDTH, -HALF_HEIGHT, 1, 1, red, green, blue, packedLight);
        poseStack.popPose();

        super.render(card, yaw, partialTick, poseStack, buffers, light);
    }

    /** Quad lies in the XZ plane with its normal pointing +Y, so a Direction rotation lays it on that face. */
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float z, float u, float v,
                               int red, int green, int blue, int light) {
        consumer.addVertex(pose, x, 0.01F, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(CardEntity card) {
        return TEXTURE;
    }
}
