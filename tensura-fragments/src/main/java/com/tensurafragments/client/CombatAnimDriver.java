package com.tensurafragments.client;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combatanim.CombatAnimations;
import com.tensurafragments.combatanim.client.CombatAnimClient;
import com.tensurafragments.combatanim.client.CombatAnimator;
import com.tensurafragments.network.CombatPosePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Picks which combat animation kit animation each player plays, from what this client knows about them. One-off moves
 * come from the server (CombatAnim.play); this keeps the rest going, most important first:
 * <ol>
 * <li>a one-off move until it's done;</li>
 * <li>the state the player is in (blocking, holding, held, knocked down, juggled, thrown, diving, tackling...);</li>
 * <li>with Combat Mode on, the boxing guard, with a left or right hook for each punch.</li>
 * </ol>
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class CombatAnimDriver {
    private static final String STANCE = "idle_guard";

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

    /** The animations this driver starts and stops by itself (the guard and the states). */
    private static final Set<String> DRIVEN = Set.of(STANCE, "block", "brawler_hold", "held", "knocked_down", "thrown",
            "air_juggle", "brawler_slam_dive", "titan_meteor_dive", "swift_dive_kick", "titan_tackle", "titan_iron_body",
            "swift_counter_stance");

    private record Swing(boolean swinging, int time) {
    }

    private static final Map<Integer, Swing> SWINGS = new HashMap<>();
    private static final Map<Integer, Integer> LAST_POSE = new HashMap<>();

    private CombatAnimDriver() {
    }

    /** Plays an animation on this player here only (the server tells everyone else itself). */
    public static void play(Player player, String name) {
        CombatAnimClient.playLocal(player, name, false);
    }

    /** Whether this player stands in the boxing guard (Combat Mode on, bare-handed, on their feet). */
    static boolean inStance(Player player) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = player == mc.player ? ClientCombat.isOn() : CombatPoses.pose(player) == CombatPosePayload.STANCE;
        return on && !player.isCrouching() && !player.getMainHandItem().isDamageableItem();
    }

    /** No combat animation while riding, swimming, gliding or asleep. */
    private static boolean animatable(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSwimming()
                && !player.isFallFlying() && !player.isSleeping();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            drive(player);
        }
        if (mc.level.getGameTime() % 200 == 0) {
            SWINGS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            LAST_POSE.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    private static void drive(AbstractClientPlayer player) {
        Swing last = SWINGS.get(player.getId());
        boolean swung = player.swinging && (last == null || !last.swinging() || player.swingTime < last.time());
        SWINGS.put(player.getId(), new Swing(player.swinging, player.swingTime));
        int pose = CombatPoses.pose(player);
        Integer before = LAST_POSE.put(player.getId(), pose);
        String current = CombatAnimator.current(player);
        if (!animatable(player)) {
            if (current != null) {
                CombatAnimator.stop(player);
            }
            return;
        }
        String state = stateAnimation(pose);
        if (before != null && before == CombatPosePayload.DOWN && pose != CombatPosePayload.DOWN
                && pose != CombatPosePayload.HELD) {
            play(player, "get_up");
            return;
        }
        // A new punch in the guard is a hook with the hand that threw it (it can cut off the last hook, not a move).
        if (swung && state == null && inStance(player)
                && (current == null || current.equals(STANCE) || current.startsWith("punch_hook_"))) {
            HumanoidArm arm = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            play(player, arm == HumanoidArm.RIGHT ? "punch_hook_right" : "punch_hook_left");
            return;
        }
        if (current != null && !DRIVEN.contains(current) && once(current)) {
            // A one-off move plays out first.
            return;
        }
        String wanted = state != null ? state : inStance(player) ? STANCE : null;
        if (wanted != null && !wanted.equals(current)) {
            play(player, wanted);
        } else if (wanted == null && current != null && DRIVEN.contains(current)) {
            CombatAnimator.stop(player);
        }
    }

    private static boolean once(String name) {
        CombatAnimations.Anim anim = CombatAnimations.get(name);
        return anim != null && anim.mode() == CombatAnimations.Mode.ONCE;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SWINGS.clear();
        LAST_POSE.clear();
    }
}
