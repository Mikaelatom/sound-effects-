package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.BeastKind;
import com.tensurafragments.shikigami.PaperBeastEntity;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** A paper beast: one of Tensura's creature models (and its animations), folded from paper. */
public class PaperBeastRenderer extends GeoEntityRenderer<PaperBeastEntity> {
    /** Parts of Tensura's hound model that belong to its other variant. */
    private static final List<String> HIDDEN_BONES = List.of("SnakeTail", "MiscSnakeTail", "ClosedEyes");

    private final BeastKind kind;

    public PaperBeastRenderer(EntityRendererProvider.Context context, BeastKind kind) {
        super(context, new DefaultedEntityGeoModel<PaperBeastEntity>(ResourceLocation.fromNamespaceAndPath("tensura", kind.model())) {
            private final ResourceLocation texture = TensuraFragments.id("textures/entity/paper/" + kind.id() + ".png");

            @Override
            public ResourceLocation getTextureResource(PaperBeastEntity animatable) {
                return texture;
            }

            @Override
            public void setCustomAnimations(PaperBeastEntity animatable, long instanceId, AnimationState<PaperBeastEntity> state) {
                super.setCustomAnimations(animatable, instanceId, state);
                for (String name : HIDDEN_BONES) {
                    GeoBone bone = getAnimationProcessor().getBone(name);
                    if (bone != null) {
                        bone.setHidden(true);
                    }
                }
            }
        });
        this.kind = kind;
        withScale(kind.renderScale());
        shadowRadius = 0.3F;
    }

    @Override
    public void render(PaperBeastEntity beast, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        poseStack.pushPose();
        poseStack.translate(0, kind.renderYOffset() * kind.renderScale(), 0);
        super.render(beast, yaw, partialTick, poseStack, buffers, light);
        poseStack.popPose();
    }
}
