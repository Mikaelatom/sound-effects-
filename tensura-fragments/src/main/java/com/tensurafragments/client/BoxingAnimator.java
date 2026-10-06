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
 * Combat Mode's body animations (made with the Punch Swish mod), seen by everyone in third person. What plays, most
 * important first:
 * <ol>
 * <li>a one-off move (a finisher, a launcher, a dash, a throw, a block hit, getting hit...) until it's done;</li>
 * <li>the state the player is in (blocking, holding, held, knocked down, juggled, thrown, diving, tackling...), looped
 * or held on its last frame;</li>
 * <li>with Combat Mode on, the boxing guard, with a left or right hook for each punch.</li>
 * </ol>
 * Switching between them crossfades over a few ticks.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class BoxingAnimator {
    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    /** Ticks a switch between animations takes. */
    private static final float BLEND_TICKS = 3;

    enum Mode {
        /** Plays through, then back to whatever's underneath. */
        ONCE,
        /** Repeats. */
        LOOP,
        /** Plays through, then stays on its last frame. */
        HOLD
    }

    /** A keyframed animation: for each part, frames of [x, y, z offset (pixels), rotation quaternion x, y, z, w]. */
    record Anim(String name, float length, float fps, Mode mode, float strike, float[][][] parts) {
        float lengthTicks() {
            return length * 20;
        }

        /** Where in the animation (seconds) it is after this many ticks of playing. */
        float time(float ticks) {
            float seconds = Math.max(0, ticks) / 20F;
            return mode == Mode.LOOP && length > 0 ? seconds % length : Math.min(seconds, length);
        }

        void sample(float seconds, int part, float[] out) {
            float[][] frames = parts[part];
            float f = Mth.clamp(seconds * fps, 0, frames.length - 1);
            int i0 = (int) f;
            int i1 = Math.min(i0 + 1, frames.length - 1);
            float t = f - i0;
            float[] a = frames[i0];
            float[] b = frames[i1];
            out[0] = Mth.lerp(t, a[0], b[0]);
            out[1] = Mth.lerp(t, a[1], b[1]);
            out[2] = Mth.lerp(t, a[2], b[2]);
            Quaternionf q = new Quaternionf(a[3], a[4], a[5], a[6]).slerp(new Quaternionf(b[3], b[4], b[5], b[6]), t);
            out[3] = q.x;
            out[4] = q.y;
            out[5] = q.z;
            out[6] = q.w;
        }
    }

    private static final Map<String, Anim> ANIMS = new HashMap<>();

    /** The state animation for each synced pose. */
    private static String stateAnimation(int pose) {
        return switch (pose) {
            case CombatPosePayload.BLOCK -> "block";
            case CombatPosePayload.GRAB -> "brawler_hold";
            case CombatPosePayload.HELD -> "held";
            case CombatPosePayload.DOWN -> "knocked_down";
            case CombatPosePayload.THROWN -> "thrown";
            case CombatPosePayload.JUGGLE -> "air_juggle";
            case CombatPosePayload.SLAM_DIVE -> "brawler_slam_dive";
            case CombatPosePayload.METEOR_DIVE -> "titan_meteor_dive";
            case CombatPosePayload.DIVE_KICK -> "swift_dive_kick";
            case CombatPosePayload.TACKLE -> "titan_tackle";
            case CombatPosePayload.IRON_BODY -> "titan_iron_body";
            case CombatPosePayload.COUNTER -> "swift_counter_stance";
            default -> null;
        };
    }

    private record Action(Anim anim, int start) {
    }


    /**
     * Where each punch's swish goes, as the Punch Swish mod has it: how far in front of the eyes, to the side and down,
     * its roll (degrees), size, and whether it's mirrored.
     */
    private record SwishStyle(double forward, double side, double up, double roll, double size, boolean mirror) {
    }

    /** Only the 5 punches get a swish. */
    private static final Map<String, SwishStyle> SWISHES = Map.of(
            "punch_jab_right", new SwishStyle(1.0, 0.22, -0.3, 25, 0.45, false),
            "punch_jab_left", new SwishStyle(1.0, -0.22, -0.3, -25, 0.45, true),
            "punch_hook_right", new SwishStyle(1.1, 0, -0.3, 0, 0.85, false),
            "punch_hook_left", new SwishStyle(1.1, 0, -0.3, 0, 0.85, true),
            "punch_uppercut_left", new SwishStyle(0.95, -0.18, -0.35, -90, 0.7, false));
    /** The swish comes this many ticks before the punch's strike. */
    private static final float SWISH_LEAD_TICKS = 2;
    /** Each player's punch whose swish has been spawned (by its start tick). */
    private static final Map<Integer, Integer> SWISHED = new HashMap<>();

    private record Swing(boolean swinging, int time) {
    }

    /** What each player was last drawn doing: which animation and the pose it ended up in, for crossfading. */
    private static final class Track {
        String source = "none";
        float switchedAt;
        /** The targets last drawn (per part), or null if nothing was. */
        float[][] last;
        /** The targets when the switch happened, to blend from (null: blend from the ordinary pose). */
        float[][] from;
        int lastPose = CombatPosePayload.NONE;
        int stateSince;
    }

    private static final Map<Integer, Action> ACTIONS = new HashMap<>();
    private static final Map<Integer, Swing> SWINGS = new HashMap<>();
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    /** Until when (tick) a move's own animation shouldn't be replaced by the hook its arm swing would start. */
    private static final Map<Integer, Integer> HELD = new HashMap<>();

    private BoxingAnimator() {
    }

    static Anim anim(String name) {
        return ANIMS.computeIfAbsent(name, BoxingAnimator::load);
    }

    private static Anim load(String name) {
        String path = "/assets/" + TensuraFragments.MODID + "/animations/" + name + ".json";
        try (InputStream in = BoxingAnimator.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
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
            Mode mode = switch (json.has("mode") ? json.get("mode").getAsString() : "once") {
                case "loop" -> Mode.LOOP;
                case "hold" -> Mode.HOLD;
                default -> Mode.ONCE;
            };
            float strike = json.has("strike") ? json.get("strike").getAsFloat() : 0;
            return new Anim(name, json.get("length").getAsFloat(), json.get("fps").getAsFloat(), mode, strike, data);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Couldn't load combat animation {}", path, e);
            return null;
        }
    }

    /** Plays a one-off animation on this player (from the server, or started here for your own uppercut). */
    public static void play(int entityId, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof Player player)) {
            return;
        }
        Anim anim = anim(name);
        if (anim == null) {
            return;
        }
        Action current = ACTIONS.get(entityId);
        if (current != null && current.anim() == anim && player.tickCount - current.start() < 4) {
            // Already playing it (started here a moment before the server said so).
            return;
        }
        ACTIONS.put(entityId, new Action(anim, player.tickCount));
        HELD.put(entityId, player.tickCount + 3);
    }

    /** Whether this player stands in the boxing guard (Combat Mode on, bare-handed, on their feet). */
    static boolean inStance(Player player) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = player == mc.player ? ClientCombat.isOn() : CombatPoses.pose(player) == CombatPosePayload.STANCE;
        return on && !player.isCrouching() && !player.getMainHandItem().isDamageableItem();
    }

    /** Animations aren't drawn at all while riding, swimming, gliding or asleep. */
    private static boolean animatable(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSwimming()
                && !player.isFallFlying() && !player.isSleeping();
    }

    /** Spots each new punch (a new arm swing) and starts the hook for that hand; starts the get-up after a knockdown. */
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
            Track track = TRACKS.computeIfAbsent(player.getId(), id -> new Track());
            int pose = CombatPoses.pose(player);
            if (pose != track.lastPose) {
                if (track.lastPose == CombatPosePayload.DOWN && pose != CombatPosePayload.HELD) {
                    play(player.getId(), "get_up");
                }
                track.lastPose = pose;
                track.stateSince = player.tickCount;
            }
            boolean held = player.tickCount <= HELD.getOrDefault(player.getId(), Integer.MIN_VALUE);
            if (started && !held && stateAnimation(pose) == null && inStance(player)) {
                HumanoidArm arm = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm()
                        : player.getMainArm().getOpposite();
                Anim hook = anim(arm == HumanoidArm.RIGHT ? "punch_hook_right" : "punch_hook_left");
                if (hook != null) {
                    ACTIONS.put(player.getId(), new Action(hook, player.tickCount - Math.max(0, player.swingTime)));
                }
            }
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            swish(mc, player);
        }
        if (mc.level.getGameTime() % 200 == 0) {
            SWISHED.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            SWINGS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            TRACKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            ACTIONS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    /** The one-off animation playing on this player right now and how far into it (seconds), or null. */
    public static Playing playing(Player player, float partialTick) {
        Action action = ACTIONS.get(player.getId());
        if (action == null || !animatable(player)) {
            return null;
        }
        float ticks = player.tickCount + partialTick - action.start();
        if (ticks < 0 || ticks >= action.anim().lengthTicks()) {
            return null;
        }
        return new Playing(action.anim().name(), ticks / 20F);
    }

    public record Playing(String animation, float seconds) {
    }

    /** A punch's swish, just before it lands, for everyone to see (you included, in first person). */
    private static void swish(Minecraft mc, AbstractClientPlayer player) {
        Action action = ACTIONS.get(player.getId());
        SwishStyle style = action == null ? null : SWISHES.get(action.anim().name());
        if (style == null || action.anim().strike() <= 0 || player.isInvisible()
                || SWISHED.getOrDefault(player.getId(), Integer.MIN_VALUE) == action.start()) {
            return;
        }
        int elapsed = player.tickCount - action.start();
        if (elapsed < action.anim().strike() * 20 - SWISH_LEAD_TICKS) {
            return;
        }
        SWISHED.put(player.getId(), action.start());
        if (elapsed > action.anim().lengthTicks()) {
            return;
        }
        net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        net.minecraft.world.phys.Vec3 side = net.minecraft.world.phys.Vec3.directionFromRotation(0, player.getYRot() + 90);
        net.minecraft.world.phys.Vec3 at = player.getEyePosition().add(look.scale(style.forward())).add(side.scale(style.side()))
                .add(0, style.up(), 0);
        mc.level.addParticle(com.tensurafragments.ModRegistries.SWISH.get(), true, at.x, at.y, at.z,
                Math.toRadians(style.roll()), style.size(), style.mirror() ? 1 : 0);
    }

    /**
     * Third person: after the usual animation, the combat animation is laid over it. Returns whether it drew one (if
     * not, the simple block and grab poses are used).
     */
    public static boolean apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks, float limbSwingAmount) {
        // First-person arms are set up with everything at 0: leave those alone.
        if (!(entity instanceof Player player) || ageInTicks == 0) {
            return false;
        }
        Track track = TRACKS.computeIfAbsent(player.getId(), id -> new Track());
        Anim anim = null;
        float ticks = 0;
        String source = "none";
        if (animatable(player)) {
            Action action = ACTIONS.get(player.getId());
            int pose = CombatPoses.pose(player);
            String state = stateAnimation(pose);
            if (action != null && ageInTicks - action.start() < action.anim().lengthTicks()) {
                anim = action.anim();
                ticks = ageInTicks - action.start();
                source = "action:" + anim.name() + ":" + action.start();
            } else if (state != null && anim(state) != null) {
                anim = anim(state);
                ticks = ageInTicks - track.stateSince;
                source = "state:" + state + ":" + track.stateSince;
            } else if (inStance(player) && anim("idle_guard") != null) {
                // The guard, gently breathing.
                anim = anim("idle_guard");
                ticks = ageInTicks;
                source = "stance";
            }
        }
        if (!source.equals(track.source)) {
            track.from = track.last;
            track.switchedAt = ageInTicks;
            track.source = source;
        }
        float blend = Mth.clamp((ageInTicks - track.switchedAt) / BLEND_TICKS, 0, 1);
        blend = blend * blend * (3 - 2 * blend);
        float[][] targets;
        float weight;
        if (anim != null) {
            targets = new float[PARTS.length][7];
            float seconds = anim.time(ticks);
            for (int p = 0; p < PARTS.length; p++) {
                anim.sample(seconds, p, targets[p]);
                if (track.from != null && blend < 1) {
                    mix(track.from[p], targets[p], blend);
                }
            }
            weight = track.from == null ? blend : 1;
        } else if (track.from != null && blend < 1) {
            // Nothing to play any more: ease back out to the ordinary pose.
            targets = track.from;
            weight = 1 - blend;
        } else {
            track.last = null;
            return false;
        }
        track.last = targets;
        if (weight <= 0) {
            return true;
        }
        boolean walking = source.equals("stance") || source.startsWith("action:punch_");
        // While walking in the guard, the legs keep walking.
        float legs = walking ? weight * (1 - Mth.clamp(limbSwingAmount * 1.5F, 0, 1)) : weight;
        part(model.head, targets[0], weight, true);
        part(model.body, targets[1], weight, false);
        part(model.rightArm, targets[2], weight, false);
        part(model.leftArm, targets[3], weight, false);
        part(model.rightLeg, targets[4], legs, false);
        part(model.leftLeg, targets[5], legs, false);
        model.hat.copyFrom(model.head);
        return true;
    }

    /** Blends one target toward another (positions lerped, rotations slerped), in place into {@code to}. */
    private static void mix(float[] from, float[] to, float t) {
        for (int i = 0; i < 3; i++) {
            to[i] = Mth.lerp(t, from[i], to[i]);
        }
        Quaternionf q = new Quaternionf(from[3], from[4], from[5], from[6]).slerp(new Quaternionf(to[3], to[4], to[5], to[6]), t);
        to[3] = q.x;
        to[4] = q.y;
        to[5] = q.z;
        to[6] = q.w;
    }

    /** Blends a part toward a target (the head's rotation is added on top of where it's looking). */
    private static void part(ModelPart part, float[] target, float weight, boolean additive) {
        if (weight <= 0) {
            return;
        }
        PartPose initial = part.getInitialPose();
        Quaternionf current = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        Quaternionf rot = new Quaternionf(target[3], target[4], target[5], target[6]);
        // The head keeps looking where the player looks; the animation's nod is added in the head's own frame (added
        // in the body's frame instead, a nod on a head turned to the side would come out as a sideways tilt).
        Quaternionf goal = additive ? new Quaternionf(current).mul(rot) : rot;
        current.slerp(goal, weight);
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
        part.x = Mth.lerp(weight, part.x, initial.x + target[0]);
        part.y = Mth.lerp(weight, part.y, initial.y + target[1]);
        part.z = Mth.lerp(weight, part.z, initial.z + target[2]);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIONS.clear();
        SWISHED.clear();
        HELD.clear();
        SWINGS.clear();
        TRACKS.clear();
    }
}
