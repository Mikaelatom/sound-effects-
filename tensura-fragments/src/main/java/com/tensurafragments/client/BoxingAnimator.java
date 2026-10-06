package com.tensurafragments.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.CombatPosePayload;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Combat Mode's boxing look (animations from the Punch Swish mod): while Combat Mode is on you stand in a boxing guard,
 * and each punch is a left or right hook, whichever hand threw it. Everyone around sees it in third person.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class BoxingAnimator {
    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /** A keyframed animation: for each part, frames of [x, y, z offset (pixels), rotation quaternion x, y, z, w]. */
    record Anim(float length, float fps, float[][][] parts) {
        float lengthTicks() {
            return length * 20;
        }

        void sample(float seconds, int part, Vector3f pos, Quaternionf rot) {
            float[][] frames = parts[part];
            float f = Mth.clamp(seconds * fps, 0, frames.length - 1);
            int i0 = (int) f;
            int i1 = Math.min(i0 + 1, frames.length - 1);
            float t = f - i0;
            float[] a = frames[i0];
            float[] b = frames[i1];
            pos.set(Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2]));
            rot.set(a[3], a[4], a[5], a[6]).slerp(new Quaternionf(b[3], b[4], b[5], b[6]), t);
        }
    }

    private static Anim hookLeft;
    private static Anim hookRight;
    /** Other animations, loaded when first played. */
    private static final Map<String, Anim> OTHERS = new HashMap<>();
    /** Until when (tick) a move's own animation shouldn't be replaced by the hook its arm swing would start. */
    private static final Map<Integer, Integer> HELD = new HashMap<>();

    private record Punch(Anim anim, int start) {
    }

    private record Swing(boolean swinging, int time) {
    }

    private static final Map<Integer, Punch> PUNCHES = new HashMap<>();
    private static final Map<Integer, Swing> SWINGS = new HashMap<>();
    /** When each player's stance started (for easing into it). */
    private static final Map<Integer, Integer> STANCE_SINCE = new HashMap<>();

    private BoxingAnimator() {
    }

    private static Anim load(String name) {
        String path = "/assets/" + TensuraFragments.MODID + "/animations/" + name + ".json";
        try (InputStream in = BoxingAnimator.class.getResourceAsStream(path)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject parts = json.getAsJsonObject("parts");
            float[][][] data = new float[PARTS.length][][];
            for (int p = 0; p < PARTS.length; p++) {
                JsonArray frames = parts.getAsJsonArray(PARTS[p]);
                data[p] = new float[frames.size()][7];
                for (int f = 0; f < frames.size(); f++) {
                    JsonArray v = frames.get(f).getAsJsonArray();
                    for (int k = 0; k < 7; k++) {
                        data[p][f][k] = v.get(k).getAsFloat();
                    }
                }
            }
            return new Anim(json.get("length").getAsFloat(), json.get("fps").getAsFloat(), data);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Couldn't load boxing animation {}", path, e);
            return null;
        }
    }

    private static boolean loaded() {
        if (hookLeft == null || hookRight == null) {
            hookLeft = load("punch_hook_left");
            hookRight = load("punch_hook_right");
        }
        return hookLeft != null && hookRight != null;
    }

    /** Plays a move's own animation (like the uppercut) on this player, instead of a hook. */
    public static void play(int entityId, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof Player player)) {
            return;
        }
        Anim anim = OTHERS.computeIfAbsent(name, BoxingAnimator::load);
        if (anim != null) {
            PUNCHES.put(entityId, new Punch(anim, player.tickCount));
            HELD.put(entityId, player.tickCount + 3);
        }
    }

    /** Whether this player stands in the boxing guard right now. */
    static boolean inStance(Player player) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = player == mc.player ? ClientCombat.isOn() : CombatPoses.pose(player) == CombatPosePayload.STANCE;
        int pose = CombatPoses.pose(player);
        return on && pose != CombatPosePayload.BLOCK && pose != CombatPosePayload.GRAB && player.isAlive()
                && !player.isSpectator() && !player.isPassenger() && !player.isSwimming() && !player.isFallFlying()
                && !player.isSleeping() && !player.isCrouching() && !player.getMainHandItem().isDamageableItem();
    }

    /** Spots each new punch (a new arm swing) and starts the hook for that hand. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            Swing last = SWINGS.get(player.getId());
            boolean started = player.swinging && (last == null || !last.swinging() || player.swingTime < last.time());
            SWINGS.put(player.getId(), new Swing(player.swinging, player.swingTime));
            if (inStance(player)) {
                STANCE_SINCE.putIfAbsent(player.getId(), player.tickCount);
                boolean held = player.tickCount <= HELD.getOrDefault(player.getId(), Integer.MIN_VALUE);
                if (started && !held && loaded()) {
                    HumanoidArm arm = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm()
                            : player.getMainArm().getOpposite();
                    PUNCHES.put(player.getId(), new Punch(arm == HumanoidArm.RIGHT ? hookRight : hookLeft,
                            player.tickCount - Math.max(0, player.swingTime)));
                }
            } else {
                STANCE_SINCE.remove(player.getId());
                PUNCHES.remove(player.getId());
            }
        }
        if (mc.level.getGameTime() % 200 == 0) {
            SWINGS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    /** Third person: after the usual animation, the guard (and any hook being thrown) is laid over it. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks, float limbSwingAmount) {
        if (!(entity instanceof Player player) || !inStance(player) || !loaded()) {
            return;
        }
        Integer since = STANCE_SINCE.get(player.getId());
        float in = since == null ? 0 : Mth.clamp((ageInTicks - since) / 4F, 0, 1);
        float weight = in * in * (3 - 2 * in);
        if (weight <= 0) {
            return;
        }
        Anim anim = hookRight;
        float seconds = 0;
        Punch punch = PUNCHES.get(player.getId());
        if (punch != null) {
            float ticks = ageInTicks - punch.start();
            if (ticks >= 0 && ticks < punch.anim().lengthTicks()) {
                anim = punch.anim();
                seconds = ticks / 20F;
            }
        }
        // Walking keeps its own legs.
        float legs = weight * (1 - Mth.clamp(limbSwingAmount * 1.5F, 0, 1));
        Vector3f pos = new Vector3f();
        Quaternionf rot = new Quaternionf();
        part(model.head, anim, 0, seconds, weight, true, pos, rot);
        part(model.body, anim, 1, seconds, weight, false, pos, rot);
        part(model.rightArm, anim, 2, seconds, weight, false, pos, rot);
        part(model.leftArm, anim, 3, seconds, weight, false, pos, rot);
        part(model.rightLeg, anim, 4, seconds, legs, false, pos, rot);
        part(model.leftLeg, anim, 5, seconds, legs, false, pos, rot);
        model.hat.copyFrom(model.head);
    }

    /** Blends a part toward the animation's pose (the head's rotation is added on top of where it's looking). */
    private static void part(ModelPart part, Anim anim, int index, float seconds, float weight, boolean additive,
                             Vector3f pos, Quaternionf rot) {
        if (weight <= 0) {
            return;
        }
        anim.sample(seconds, index, pos, rot);
        PartPose initial = part.getInitialPose();
        Quaternionf current = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        // The head keeps looking where the player looks; the animation's nod is added in the head's own frame (added
        // in the body's frame instead, a nod on a head turned to the side would come out as a sideways tilt).
        Quaternionf target = additive ? new Quaternionf(current).mul(rot) : new Quaternionf(rot);
        current.slerp(target, weight);
        float x = current.x;
        float y = current.y;
        float z = current.z;
        float w = current.w;
        float m20 = 2 * (x * z - w * y);
        float m21 = 2 * (y * z + w * x);
        float m22 = 1 - 2 * (x * x + y * y);
        float m10 = 2 * (x * y + w * z);
        float m00 = 1 - 2 * (y * y + z * z);
        part.xRot = (float) Math.atan2(m21, m22);
        part.yRot = (float) Math.asin(Mth.clamp(-m20, -1, 1));
        part.zRot = (float) Math.atan2(m10, m00);
        part.x = Mth.lerp(weight, part.x, initial.x + pos.x());
        part.y = Mth.lerp(weight, part.y, initial.y + pos.y());
        part.z = Mth.lerp(weight, part.z, initial.z + pos.z());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PUNCHES.clear();
        HELD.clear();
        SWINGS.clear();
        STANCE_SINCE.clear();
    }
}
