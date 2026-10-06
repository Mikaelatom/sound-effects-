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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.player == null || mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (AbstractClientPlayer player : mc.level.players()) {
            drawUppercut(event, player, partial);
        }
        if (!mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        AbstractClientPlayer player = mc.player;
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

    /** When the uppercut's swish shows (seconds into the punch) and for how long, as the Punch Swish mod has it. */
    private static final float UPPERCUT_FROM = 0.15F;
    private static final float UPPERCUT_FOR = 0.4F;

    /**
     * The uppercut's swish as the Punch Swish mod draws it: facing the camera, a little ahead of the eyes, left of
     * centre and below them, turned a quarter so the arc rises; it keeps up with the player as the uppercut lifts them.
     */
    private static void drawUppercut(RenderLevelStageEvent event, AbstractClientPlayer player, float partial) {
        BoxingAnimator.Playing playing = BoxingAnimator.playing(player, partial);
        if (playing == null || player.isInvisible()
                || !com.tensurafragments.network.CombatAnimPayload.UPPERCUT.equals(playing.animation())) {
            return;
        }
        float into = (playing.seconds() - UPPERCUT_FROM) / UPPERCUT_FOR;
        if (into < 0 || into >= 1) {
            return;
        }
        int frame = Mth.clamp((int) (into * ModelSwish.FRAMES), 0, ModelSwish.FRAMES - 1);
        float pitch = player.getViewXRot(partial);
        float yaw = player.getViewYRot(partial);
        Vec3 at = player.getEyePosition(partial)
                .add(Vec3.directionFromRotation(pitch, yaw).scale(0.95))
                .add(Vec3.directionFromRotation(0, yaw + 90).scale(-0.18))
                .add(0, -0.35, 0)
                .subtract(event.getCamera().getPosition());
        org.joml.Quaternionf turn = new org.joml.Quaternionf(event.getCamera().rotation())
                .rotateZ((float) Math.toRadians(-90));
        float size = 0.7F;
        float u0 = 0;
        float u1 = 1;
        float v0 = frame / (float) ModelSwish.FRAMES;
        float v1 = (frame + 1) / (float) ModelSwish.FRAMES;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffer(buffers);
        PoseStack.Pose pose = event.getPoseStack().last();
        float[][] corners = {{-1, -1, u1, v1}, {-1, 1, u1, v0}, {1, 1, u0, v0}, {1, -1, u0, v1}};
        for (float[] c : corners) {
            org.joml.Vector3f p = new org.joml.Vector3f(c[0], c[1], 0).rotate(turn).mul(size)
                    .add((float) at.x, (float) at.y, (float) at.z);
            out.addVertex(pose, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(c[2], c[3])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
        buffers.endBatch(RenderType.entityTranslucentEmissive(ModelSwish.TEXTURE));
    }
}
