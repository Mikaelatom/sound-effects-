package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.spiritrace.ReleaseAuraEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Spirit Release's aura on Tensura's Haki model: blue (10%), rainbow (50%: a white copy painted with moving rainbow
 * bands) or Tensura's own purple Demon Lord Haki (100%).
 */
public class ReleaseAuraRenderer extends GeoEntityRenderer<ReleaseAuraEntity> {
    private static final ResourceLocation BLUE = TensuraFragments.id("textures/entity/release_aura_blue.png");
    private static final ResourceLocation WHITE = TensuraFragments.id("textures/entity/release_aura_white.png");
    private static final ResourceLocation PURPLE =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/field/haki/haki_demon_lord.png");

    public ReleaseAuraRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<ReleaseAuraEntity>(ResourceLocation.fromNamespaceAndPath("tensura", "misc/haki")) {
            @Override
            public ResourceLocation getTextureResource(ReleaseAuraEntity aura) {
                return texture(aura);
            }

            @Override
            public RenderType getRenderType(ReleaseAuraEntity aura, ResourceLocation texture) {
                return RenderType.entityTranslucentEmissive(texture);
            }
        });
        shadowRadius = 0;
    }

    private static ResourceLocation texture(ReleaseAuraEntity aura) {
        return aura.percent() >= 100 ? PURPLE : aura.percent() >= 50 ? WHITE : BLUE;
    }

    @Override
    public ResourceLocation getTextureLocation(ReleaseAuraEntity aura) {
        return texture(aura);
    }

    @Override
    public void render(ReleaseAuraEntity aura, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        // Bigger the more is released.
        float scale = aura.percent() >= 100 ? 0.5F : aura.percent() >= 50 ? 0.42F : 0.36F;
        MultiBufferSource target = buffers;
        if (aura.percent() >= 50 && aura.percent() < 100) {
            float hue = ((aura.tickCount + partialTick) * 0.015F) % 1F;
            target = new RainbowBufferSource(buffers, hue, 1.0F, 0.6F);
        }
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.render(aura, yaw, partialTick, poseStack, target, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public @Nullable RenderType getRenderType(ReleaseAuraEntity aura, ResourceLocation texture,
                                              @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(ReleaseAuraEntity aura, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.85F);
    }
}
