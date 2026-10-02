package com.tensurafragments.ally;

import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.soul.SoulBond;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Makes a creature serve a player: it never goes for them or their other creatures, fights whatever they're fighting,
 * and otherwise stays close. Used for Soul Reaper's souls and the Sealing Grimoire's released creatures.
 *
 * <p>Works for both kinds of mob AI. Goal-driven mobs (most vanilla ones) keep their target in {@link Mob#getTarget};
 * brain-driven ones (most of Tensura's, through SmartBrainLib) keep it in brain memories, which blocking
 * {@code setTarget} alone doesn't touch, so those are cleaned and set too.
 */
public final class Allies {
    /** Brain memories that make a mob go for someone. */
    private static final List<MemoryModuleType<? extends LivingEntity>> TARGET_MEMORIES = List.of(
            MemoryModuleType.ATTACK_TARGET, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_ATTACKABLE);

    private Allies() {
    }

    /** Never turned on: the player, their tamed animals, their souls and their grimoire's creatures. */
    public static boolean isFriendly(@Nullable Entity entity, ServerPlayer player) {
        return entity != null && (entity == player
                || (entity instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID()))
                || SoulBond.isBoundTo(entity, player)
                || Binding.isBoundTo(entity, player));
    }

    /** Call every tick for a creature serving {@code owner}. */
    public static void serve(Mob mob, ServerPlayer owner) {
        // Every tick, so a brain never gets as far as attacking.
        forgetFriendlyTargets(mob, owner);
        if (mob.tickCount % 5 != 0) {
            return;
        }
        // Fight whatever the owner is fighting; otherwise stay close.
        LivingEntity target = pickTarget(owner, mob);
        if (target != null) {
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

    private static void forgetFriendlyTargets(Mob mob, ServerPlayer owner) {
        if (mob.getTarget() != null && isFriendly(mob.getTarget(), owner)) {
            mob.setTarget(null);
        }
        Brain<?> brain = mob.getBrain();
        for (MemoryModuleType<? extends LivingEntity> memory : TARGET_MEMORIES) {
            if (brain.checkMemory(memory, MemoryStatus.REGISTERED) && isFriendly(brain.getMemory(memory).orElse(null), owner)) {
                brain.eraseMemory(memory);
            }
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)
                && brain.getMemory(MemoryModuleType.ANGRY_AT)
                .map(id -> isFriendly(((ServerLevel) mob.level()).getEntity(id), owner)).orElse(false)) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }

    @Nullable
    private static LivingEntity pickTarget(ServerPlayer owner, Mob mob) {
        for (LivingEntity candidate : new LivingEntity[] {owner.getLastHurtMob(), owner.getLastHurtByMob()}) {
            if (candidate != null && candidate.isAlive() && candidate != mob && !isFriendly(candidate, owner)
                    && candidate.level() == mob.level() && candidate.distanceToSqr(mob) < 32 * 32) {
                return candidate;
            }
        }
        return null;
    }
}
