package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.card.CardTornadoEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** Spell Card tornadoes: Tensura's magic tornado model in wind, fire or storm colours, spinning, sized to their reach. */
public class CardTornadoRenderer extends GeoEntityRenderer<CardTornadoEntity> {
    private static final ResourceLocation WIND = TensuraFragments.id("textures/entity/wind_tornado.png");
    private static final ResourceLocation FIRE = TensuraFragments.id("textures/entity/fire_whirl.png");
    private static final ResourceLocation STORM = TensuraFragments.id("textures/entity/storm_tornado.png");
    /** The model's widest ring is 11 pixels out. */
    private static final float MODEL_RADIUS = 11F / 16F;

    public CardTornadoRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<CardTornadoEntity>(ResourceLocation.fromNamespaceAndPath("tensura", "misc/magic_tornado")) {
            @Override
            public ResourceLocation getTextureResource(CardTornadoEntity animatable) {
                return texture(animatable);
            }

            @Override
            public RenderType getRenderType(CardTornadoEntity animatable, ResourceLocation texture) {
                return RenderType.entityTranslucentEmissive(texture);
            }
        });
        shadowRadius = 0;
    }

    private static ResourceLocation texture(CardTornadoEntity tornado) {
        return switch (tornado.variant()) {
            case FIRE -> FIRE;
            case STORM -> STORM;
            default -> WIND;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(CardTornadoEntity tornado) {
        return texture(tornado);
    }

    @Override
    public void render(CardTornadoEntity tornado, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        poseStack.pushPose();
        float scale = (float) tornado.radius() / MODEL_RADIUS;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees((tornado.tickCount + partialTick) * -30F));
        super.render(tornado, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public @Nullable RenderType getRenderType(CardTornadoEntity tornado, ResourceLocation texture,
                                              @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(CardTornadoEntity tornado, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.9F);
    }
}
