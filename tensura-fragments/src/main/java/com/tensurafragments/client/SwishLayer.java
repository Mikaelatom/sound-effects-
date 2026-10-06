package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tensurafragments.TensuraFragments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws each punch's swish on the player model (third person), and on your own punches in first person. */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public class SwishLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public SwishLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(ModelSwish.TEXTURE));
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        BoxingAnimator.Playing playing = BoxingAnimator.playing(player, partialTick);
        if (playing == null || player.isInvisible() || !ModelSwish.showing(playing.animation(), playing.seconds())) {
            return;
        }
        ModelSwish.render(getParentModel(), playing.animation(), playing.seconds(), stack, buffer(buffers));
    }

    /**
     * First person: your own body isn't drawn, so the swish is drawn here, on your model posed as it would be, facing
     * where you look.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.player == null
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        AbstractClientPlayer player = mc.player;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BoxingAnimator.Playing playing = BoxingAnimator.playing(player, partial);
        if (playing == null || !ModelSwish.showing(playing.animation(), playing.seconds())
                || !(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        float age = player.tickCount + partial;
        // Pose the model the way the third-person view would (this runs the combat animation on it).
        model.setupAnim(player, 0, 0, age, 0, 0);
        Vec3 camera = event.getCamera().getPosition();
        Vec3 at = player.getPosition(partial);
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        // As the player renderer places the model: turned to face where you look, flipped, raised to the feet.
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot)));
        stack.scale(-1, -1, 1);
        stack.translate(0, -1.501F, 0);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ModelSwish.render(model, playing.animation(), playing.seconds(), stack, buffer(buffers));
        buffers.endBatch(RenderType.entityTranslucentEmissive(ModelSwish.TEXTURE));
        stack.popPose();
    }
}
