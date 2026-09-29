package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tensurafragments.flame.HellStormEntity;
import com.tensurafragments.flame.HellStormParts;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Gluttony's mist (Tensura's own model, texture and animation) pouring from the caster's hand along their aim,
 * sized to exactly the cone the storm burns ({@link HellStormParts}) and burned hellfire red.
 */
public class HellStormRenderer extends GeoEntityRenderer<HellStormEntity> {
    private final HandAnchor anchor = new HandAnchor();

    public HellStormRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<HellStormEntity>(ResourceLocation.fromNamespaceAndPath("tensura", "misc/gluttony_mist")) {
            @Override
            public RenderType getRenderType(HellStormEntity animatable, ResourceLocation texture) {
                return RenderType.entityTranslucentEmissive(texture);
            }
        });
        shadowRadius = 0;
    }

    @Override
    protected void applyRotations(HellStormEntity storm, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        // The mist's own animation already lays it down along -Z (its root bone turns 90 degrees), the same way
        // Tensura aims it: face the caster's aim, then stretch it to the storm's length and cone.
        poseStack.mulPose(Axis.YP.rotationDegrees(180F - anchor.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-anchor.pitch));
        float widthScale = storm.getEndRadius() / HellStormParts.MODEL_END_RADIUS;
        poseStack.scale(widthScale, widthScale, storm.getLength() / HellStormParts.MODEL_LENGTH);
    }

    @Override
    public void render(HellStormEntity storm, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        anchor.pin(storm, storm.getCasterId(), partialTick, poseStack);
        super.render(storm, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** The storm reaches far beyond its tiny entity box, so never cull it by that box. */
    @Override
    public boolean shouldRender(HellStormEntity storm, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public @Nullable RenderType getRenderType(HellStormEntity animatable, ResourceLocation texture,
                                              @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(HellStormEntity animatable, float partialTick, int packedLight) {
        // Gluttony's dark mist, turned to hellfire.
        return Color.ofRGBA(1.0F, 0.42F, 0.15F, 0.95F);
    }
}
