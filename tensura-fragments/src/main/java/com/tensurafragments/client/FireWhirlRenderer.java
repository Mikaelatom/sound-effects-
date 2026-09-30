package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tensurafragments.yifa.FireWhirlEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** The fire whirl: Tensura's magic tornado model and wind texture, burned orange and sized to what it hits. */
public class FireWhirlRenderer extends GeoEntityRenderer<FireWhirlEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("tensura",
            "textures/entity/misc/wind_tornado.png");
    /** The model's widest ring is 11 pixels out. */
    private static final float MODEL_RADIUS = 11F / 16F;

    public FireWhirlRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<FireWhirlEntity>(ResourceLocation.fromNamespaceAndPath("tensura", "misc/magic_tornado")) {
            @Override
            public ResourceLocation getTextureResource(FireWhirlEntity animatable) {
                return TEXTURE;
            }

            @Override
            public RenderType getRenderType(FireWhirlEntity animatable, ResourceLocation texture) {
                return RenderType.entityTranslucentEmissive(texture);
            }
        });
        shadowRadius = 0;
    }

    @Override
    public void render(FireWhirlEntity whirl, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        poseStack.pushPose();
        float scale = (float) whirl.radius() / MODEL_RADIUS;
        poseStack.scale(scale, scale, scale);
        // Spin the whole whirl on top of its own animation, so it's always visibly turning.
        poseStack.mulPose(Axis.YP.rotationDegrees((whirl.tickCount + partialTick) * -24F));
        super.render(whirl, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public @Nullable RenderType getRenderType(FireWhirlEntity whirl, ResourceLocation texture,
                                              @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(FireWhirlEntity whirl, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0F, 0.5F, 0.15F, 0.9F);
    }
}
