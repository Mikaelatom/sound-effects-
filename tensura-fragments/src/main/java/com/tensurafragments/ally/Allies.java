package com.tensurafragments.ally;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.soul.SoulBond;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Makes a creature serve a player: it only ever attacks what that player has hit (nothing else: not what hits you,
 * not what hits it, not what it would naturally hunt), and otherwise stays close. Used for every skill's summons:
 * Soul Reaper's souls, the Sealing Grimoire's released creatures, and Shikigami Control's shikigami and paper beasts.
 *
 * <p>Works for both kinds of mob AI. Goal-driven mobs (most vanilla ones) keep their target in {@link Mob#getTarget};
 * brain-driven ones (most of Tensura's, through SmartBrainLib) keep it in brain memories, which blocking
 * {@code setTarget} alone doesn't touch, so those are cleaned and set too.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class Allies {
    /** How long after you last hit something your creatures keep after it. */
    private static final int MARK_TICKS = 30 * 20;
    /** What each player last hit, and when. */
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    private record Mark(WeakReference<LivingEntity> target, long time) {
    }

    /** Brain memories that make a mob go for someone. */
    private static final List<MemoryModuleType<? extends LivingEntity>> TARGET_MEMORIES = List.of(
            MemoryModuleType.ATTACK_TARGET, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_ATTACKABLE);

    private Allies() {
    }

    /**
     * Never turned on: the player, their tamed animals, their souls and their grimoire's creatures, and their allies
     * (with all of theirs).
     */
    public static boolean isFriendly(@Nullable Entity entity, ServerPlayer player) {
        return entity != null && (entity == player
                || (entity instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID()))
                || SoulBond.isBoundTo(entity, player)
                || Binding.isBoundTo(entity, player)
                || Alliances.isAlliedWith(entity, player));
    }

    /** You hit something: that's what your creatures go after (for the next 30 seconds, or until you hit another). */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer player && victim != player && !isFriendly(victim, player)) {
            MARKS.put(player.getUUID(), new Mark(new WeakReference<>(victim), player.level().getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MARKS.remove(event.getEntity().getUUID());
    }

    /** What {@code owner}'s creatures may attack right now: the last thing they hit, if it's still fair game. */
    @Nullable
    public static LivingEntity target(@Nullable ServerPlayer owner) {
        Mark mark = owner == null ? null : MARKS.get(owner.getUUID());
        LivingEntity target = mark == null ? null : mark.target().get();
        if (target == null || !target.isAlive() || target.level() != owner.level()
                || owner.level().getGameTime() - mark.time() > MARK_TICKS || isFriendly(target, owner)) {
            return null;
        }
        return target;
    }

    /** Whether a creature of {@code owner}'s may set its sights on {@code target} (clearing a target is always fine). */
    public static boolean mayTarget(@Nullable LivingEntity target, ServerPlayer owner) {
        return target == null || target == target(owner);
    }

    /** Call every tick for a creature serving {@code owner}. */
    public static void serve(Mob mob, ServerPlayer owner) {
        // Every tick, so a brain never gets as far as attacking anything else.
        forgetOtherTargets(mob, owner);
        if (mob.tickCount % 5 != 0) {
            return;
        }
        // Go after what the owner hit; otherwise stay close.
        LivingEntity target = target(owner);
        if (target != null && target != mob && target.distanceToSqr(mob) < 48 * 48) {
            if (mob.getTarget() != target) {
                mob.setTarget(target);
            }
            Brain<?> brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
            }
        }
        if (mob.getTarget() == null && mob.level() == owner.level() && mob.distanceToSqr(owner) > 10 * 10) {
            mob.getNavigation().moveTo(owner, 1.2);
        }
    }

    /** Drops any target (goal or brain memory) that isn't what the owner hit. */
    private static void forgetOtherTargets(Mob mob, ServerPlayer owner) {
        LivingEntity allowed = target(owner);
        if (mob.getTarget() != null && mob.getTarget() != allowed) {
            mob.setTarget(null);
        }
        Brain<?> brain = mob.getBrain();
        for (MemoryModuleType<? extends LivingEntity> memory : TARGET_MEMORIES) {
            if (brain.checkMemory(memory, MemoryStatus.REGISTERED)) {
                LivingEntity remembered = brain.getMemory(memory).orElse(null);
                if (remembered != null && remembered != allowed) {
                    brain.eraseMemory(memory);
                }
            }
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)
                && brain.getMemory(MemoryModuleType.ANGRY_AT)
                .map(id -> allowed == null || !id.equals(allowed.getUUID())).orElse(false)) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }
}
