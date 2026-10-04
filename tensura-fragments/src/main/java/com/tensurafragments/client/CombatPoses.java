package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.CombatPosePayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Combat Mode stances drawn on players: arms crossed up in front of the face while blocking, both arms out holding
 * while grabbing. Seen by everyone in third person, and by you in first person.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class CombatPoses {
    private static final Map<Integer, Integer> POSES = new HashMap<>();
    /** When each player's stance last changed (client ticks), for easing into it. */
    private static final Map<Integer, Integer> CHANGED = new HashMap<>();

    /** While drawing the first-person arms (that has its own placement). */
    private static boolean drawingHands;

    private CombatPoses() {
    }

    public static void set(int entityId, int pose) {
        Minecraft mc = Minecraft.getInstance();
        if (pose == CombatPosePayload.NONE) {
            POSES.remove(entityId);
        } else {
            POSES.put(entityId, pose);
        }
        CHANGED.put(entityId, mc.player == null ? 0 : mc.player.tickCount);
    }

    public static int pose(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && ClientCombat.isBlocking() && !POSES.containsKey(entity.getId())) {
            // Your own block shows at once, before the server's word comes back.
            return CombatPosePayload.BLOCK;
        }
        return POSES.getOrDefault(entity.getId(), CombatPosePayload.NONE);
    }

    /** 0 to 1 over the first few ticks of a stance. */
    private static float ease(LivingEntity entity, float ageInTicks) {
        Minecraft mc = Minecraft.getInstance();
        Integer changed = CHANGED.get(entity.getId());
        if (changed == null || mc.player == null) {
            return 1;
        }
        float since = mc.player.tickCount + (ageInTicks - (int) ageInTicks) - changed;
        float t = Mth.clamp(since / 3F, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Third person: called after the model has set up its usual animation. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof Player) || drawingHands) {
            return;
        }
        int pose = pose(entity);
        if (pose == CombatPosePayload.NONE) {
            return;
        }
        float t = ease(entity, ageInTicks);
        float breathe = Mth.sin(ageInTicks * 0.12F) * 0.03F;
        if (pose == CombatPosePayload.BLOCK) {
            // Forearms up and crossed in front of the face.
            pose(model.rightArm, -1.95F + breathe, -0.75F, 0.1F, t);
            pose(model.leftArm, -1.95F + breathe, 0.75F, -0.1F, t);
        } else {
            // Both arms out in front, holding what's grabbed up.
            float strain = Mth.sin(ageInTicks * 0.9F) * 0.025F;
            pose(model.rightArm, -1.75F + strain, -0.28F, 0F, t);
            pose(model.leftArm, -1.75F - strain, 0.28F, 0F, t);
        }
    }

    private static void pose(net.minecraft.client.model.geom.ModelPart arm, float x, float y, float z, float t) {
        arm.xRot = Mth.lerp(t, arm.xRot, x);
        arm.yRot = Mth.lerp(t, arm.yRot, y);
        arm.zRot = Mth.lerp(t, arm.zRot, z);
    }

    /** First person: both arms drawn in the stance, instead of the usual hands. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible()) {
            return;
        }
        int pose = pose(mc.player);
        if (pose == CombatPosePayload.NONE) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        float t = ease(mc.player, mc.player.tickCount + event.getPartialTick());
        float age = mc.player.tickCount + event.getPartialTick();
        arm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), mc.player, true, pose, t, age);
        arm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), mc.player, false, pose, t, age);
    }

    private static void arm(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player, boolean right,
            int pose, float t, float age) {
        float f = right ? 1F : -1F;
        stack.pushPose();
        // Where vanilla puts an empty hand...
        stack.translate(f * 0.64000005F, -0.6F, -0.71999997F);
        // ...then raised into the stance, turning about the arm's own base.
        if (pose == CombatPosePayload.BLOCK) {
            // Up and tipped inwards so the forearms cross the middle of the view.
            stack.translate(f * Mth.lerp(t, 0, -0.22F), Mth.lerp(t, -0.4F, 0.3F) + Mth.sin(age * 0.12F) * 0.008F, 0);
            stack.mulPose(Axis.ZP.rotationDegrees(f * Mth.lerp(t, 0, 70F)));
        } else {
            // Both reaching forward, raised to hold.
            stack.translate(f * Mth.lerp(t, 0, -0.1F), Mth.lerp(t, -0.4F, 0.14F) + Mth.sin(age * 0.9F) * 0.006F,
                    Mth.lerp(t, 0, -0.1F));
        }
        stack.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        stack.translate(f * -1.0F, 3.6F, 3.5F);
        stack.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        stack.mulPose(Axis.XP.rotationDegrees(200.0F));
        stack.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        stack.translate(f * 5.6F, 0.0F, 0.0F);
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        drawingHands = true;
        try {
            if (right) {
                renderer.renderRightHand(stack, buffers, light, player);
            } else {
                renderer.renderLeftHand(stack, buffers, light, player);
            }
        } finally {
            drawingHands = false;
        }
        stack.popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        POSES.clear();
        CHANGED.clear();
    }
}
