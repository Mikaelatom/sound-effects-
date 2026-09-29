package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tensurafragments.flame.HellCircleEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Tensura's magic circle model with its flame texture, held upright in front of the caster's hand. */
public class HellCircleRenderer extends GeoEntityRenderer<HellCircleEntity> {
    private final HandAnchor anchor = new HandAnchor();
    private static final ResourceLocation FLAME = ResourceLocation.fromNamespaceAndPath("tensura",
            "textures/entity/misc/magic_circle/flame.png");

    public HellCircleRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<HellCircleEntity>(ResourceLocation.fromNamespaceAndPath("tensura", "misc/magic_circle")) {
            @Override
            public ResourceLocation getTextureResource(HellCircleEntity animatable) {
                return FLAME;
            }

            @Override
            public RenderType getRenderType(HellCircleEntity animatable, ResourceLocation texture) {
                return RenderType.entityTranslucentEmissive(texture);
            }
        });
        shadowRadius = 0;
    }

    @Override
    protected void applyRotations(HellCircleEntity circle, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        // The model lies flat; stand it up facing the way the caster looks, then shrink it to hand size.
        poseStack.mulPose(Axis.YP.rotationDegrees(-anchor.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90 + anchor.pitch));
        float grow = Math.min(1F, (circle.tickCount + partialTick) / 10F);
        poseStack.scale(0.3F * grow, 0.3F * grow, 0.3F * grow);
    }

    @Override
    public void render(HellCircleEntity circle, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        anchor.pin(circle, circle.getCasterId(), partialTick, poseStack);
        super.render(circle, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(HellCircleEntity circle, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public @Nullable RenderType getRenderType(HellCircleEntity animatable, ResourceLocation texture,
                                              @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
