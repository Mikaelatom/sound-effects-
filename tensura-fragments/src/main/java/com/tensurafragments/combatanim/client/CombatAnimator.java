package com.tensurafragments.combatanim.client;

import com.tensurafragments.combatanim.CombatAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks which players are playing an animation and poses their model parts.
 * Times are in ticks of entity age.
 */
public final class CombatAnimator {
    private static final float FADE_IN_TICKS = 2f;
    private static final float FADE_OUT_TICKS = 4f;
    // The swish trails are drawn by SwishTrails.Layer straight from the animation time, like in Blockbench.

    private static final class State {
        CombatAnimations.Anim anim;
        String name;
        float start;
        boolean fromRest;
        float stopAt = -1f;   // when a stop() began fading the pose out
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private CombatAnimator() {}

    /** Start an animation on this player, replacing whatever it was playing. Returns false if the animation doesn't exist. */
    public static boolean play(Player player, String name) {
        CombatAnimations.Anim anim = CombatAnimations.get(name);
        if (anim == null) return false;
        State old = STATES.get(player.getId());
        State s = new State();
        s.anim = anim;
        s.name = name;
        s.start = player.tickCount;
        // Blend in from the vanilla pose unless we're chaining straight from another animation.
        s.fromRest = old == null || old.stopAt >= 0;
        STATES.put(player.getId(), s);
        CombatFx.onPlay(player, name, anim, s.start);
        return true;
    }

    /** True while this player is still on the animation started at {@code start} (not stopped or replaced). */
    static boolean isPlaying(Player player, String name, float start) {
        State s = STATES.get(player.getId());
        return s != null && s.stopAt < 0 && s.start == start && s.name.equals(name);
    }

    /** Blend this player back to the normal pose. */
    public static void stop(Player player) {
        State s = STATES.get(player.getId());
        if (s != null && s.stopAt < 0) s.stopAt = player.tickCount;
    }

    /** The animation this player is playing, or null if none. */
    public static String current(Player player) {
        State s = STATES.get(player.getId());
        if (s == null || s.stopAt >= 0) return null;
        if (s.anim.mode() == CombatAnimations.Mode.ONCE && player.tickCount - s.start > s.anim.lengthTicks()) return null;
        return s.name;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            STATES.clear();
            return;
        }
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player) || entity.isRemoved()) {
                it.remove();
                continue;
            }
            State s = entry.getValue();
            float now = player.tickCount;
            if (s.stopAt >= 0) {
                if (now - s.stopAt > FADE_OUT_TICKS) it.remove();
                continue;
            }
            if (s.anim.mode() == CombatAnimations.Mode.ONCE && now - s.start > s.anim.lengthTicks() + FADE_OUT_TICKS) {
                it.remove();
            }
        }
    }

    /** Which animation this player is on and how far into it (seconds), for the swish trails; null if none. */
    public static SwishTrails.Time trailTime(Player player, float ageInTicks) {
        State s = STATES.get(player.getId());
        if (s == null || s.stopAt >= 0) return null;
        float t = ageInTicks - s.start;
        if (t < 0f) return null;
        if (s.anim.mode() == CombatAnimations.Mode.LOOP) t %= s.anim.lengthTicks();
        else if (t > s.anim.lengthTicks()) return null;
        return new SwishTrails.Time(s.name, t / 20f);
    }

    /** The pose to apply this frame, or null if the player isn't animating. */
    public static Pose sample(Player player, float ageInTicks) {
        State s = STATES.get(player.getId());
        if (s == null) return null;
        float t = ageInTicks - s.start;
        // First-person arm rendering calls setupAnim with an age of 0; leave that alone.
        if (t < -1f) return null;
        t = Math.max(t, 0f);
        float len = s.anim.lengthTicks();

        float seconds;
        float weight = 1f;
        if (s.fromRest && t < FADE_IN_TICKS) weight = t / FADE_IN_TICKS;
        CombatAnimations.Mode mode = s.anim.mode();
        if (mode == CombatAnimations.Mode.LOOP) {
            seconds = (t % len) / 20f;
        } else {
            seconds = Math.min(t, len) / 20f;
            if (mode == CombatAnimations.Mode.ONCE && t > len) weight = Math.min(weight, 1f - (t - len) / FADE_OUT_TICKS);
        }
        if (s.stopAt >= 0) weight = Math.min(weight, 1f - (ageInTicks - s.stopAt) / FADE_OUT_TICKS);
        if (weight <= 0f) return null;
        return new Pose(s.anim, seconds, Mth.clamp(weight, 0f, 1f));
    }

    public record Pose(CombatAnimations.Anim anim, float seconds, float weight) {
        private static final Vector3f POS = new Vector3f();
        private static final Quaternionf ROT = new Quaternionf();

        /** Blend the part from its vanilla pose toward the animation. {@code keepLook} layers the head's look direction on top. */
        public void apply(ModelPart part, int index, boolean keepLook) {
            anim.sample(seconds, index, POS, ROT);
            PartPose rest = part.getInitialPose();
            Quaternionf current = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
            Quaternionf target = new Quaternionf(ROT);
            if (keepLook) target.mul(current);
            current.slerp(target, weight);

            // Back to the ZYX Euler angles ModelPart uses (rotationZYX(zRot, yRot, xRot)).
            float x = current.x, y = current.y, z = current.z, w = current.w;
            float r20 = 2f * (x * z - w * y);
            float r21 = 2f * (y * z + w * x);
            float r22 = 1f - 2f * (x * x + y * y);
            float r10 = 2f * (x * y + w * z);
            float r00 = 1f - 2f * (y * y + z * z);
            part.xRot = (float) Math.atan2(r21, r22);
            part.yRot = (float) Math.asin(Mth.clamp(-r20, -1f, 1f));
            part.zRot = (float) Math.atan2(r10, r00);

            part.x = Mth.lerp(weight, part.x, rest.x + POS.x);
            part.y = Mth.lerp(weight, part.y, rest.y + POS.y);
            part.z = Mth.lerp(weight, part.z, rest.z + POS.z);
        }
    }
}
