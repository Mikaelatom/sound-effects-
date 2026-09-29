package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tensurafragments.flame.HellStormEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Gluttony's mist (Tensura's own model, texture and animation), pointed along the caster's aim, stretched to the
 * storm's length and burned hellfire red.
 */
public class HellStormRenderer extends GeoEntityRenderer<HellStormEntity> {
    /** The mist model is about 10 blocks tall. */
    private static final float MODEL_LENGTH = 10F;

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
        // The model points up; turn it to point along the caster's look.
        float yaw = Mth.rotLerp(partialTick, storm.yRotO, storm.getYRot());
        float pitch = Mth.lerp(partialTick, storm.xRotO, storm.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90 + pitch));
        float lengthScale = storm.getLength() / MODEL_LENGTH;
        poseStack.scale(0.9F, lengthScale, 0.9F);
    }

    @Override
    public void render(HellStormEntity storm, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        super.render(storm, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
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
