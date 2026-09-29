package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensurafragments.spirit.SpiritEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** A spirit: Tensura's own model, texture and attack animation for it, glowing and fading in and out. */
public class SpiritRenderer extends GeoEntityRenderer<SpiritEntity> {
    public SpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<SpiritEntity>() {
            @Override
            public ResourceLocation getModelResource(SpiritEntity spirit) {
                return spirit.getKind().model();
            }

            @Override
            public ResourceLocation getTextureResource(SpiritEntity spirit) {
                return spirit.getKind().texture();
            }

            @Override
            public ResourceLocation getAnimationResource(SpiritEntity spirit) {
                return spirit.getKind().animations();
            }

            @Override
            public RenderType getRenderType(SpiritEntity spirit, ResourceLocation texture) {
                return RenderType.entityTranslucent(texture);
            }
        });
        shadowRadius = 0;
    }

    @Override
    public void render(SpiritEntity spirit, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        poseStack.pushPose();
        float scale = spirit.getKind().scale();
        poseStack.scale(scale, scale, scale);
        super.render(spirit, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public @Nullable RenderType getRenderType(SpiritEntity spirit, ResourceLocation texture,
                                              @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(SpiritEntity spirit, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.85F * spirit.visibility(partialTick));
    }
}
