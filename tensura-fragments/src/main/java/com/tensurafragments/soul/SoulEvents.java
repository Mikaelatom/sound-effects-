package com.tensurafragments.soul;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.SoulEntityPayload;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Souls from kills, and keeping summoned and possessed creatures loyal (and summoned ones on their timer). */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class SoulEvents {
    private SoulEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        ServerPlayer reaper = SoulReaper.reaper(event.getSource().getEntity());
        if (reaper != null) {
            SoulReaper.onKill(reaper, victim);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Soul points also change outside this skill (Tensura's own gains and death penalty).
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0 && SoulReaper.hasSkill(player)) {
            SoulReaper.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SoulReaper.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SoulReaper.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof Mob mob)) {
            return;
        }
        SoulBond bond = SoulBond.get(entity);
        if (bond == null) {
            return;
        }
        ServerPlayer owner = level.getPlayerByUUID(bond.owner()) instanceof ServerPlayer p ? p : null;
        if (bond.summoned()) {
            // Ghosts don't burn in the sun.
            mob.clearFire();
            if (owner == null || !owner.isAlive() || level.getGameTime() >= bond.until()) {
                level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 20,
                        0.3, 0.5, 0.3, 0.05);
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 1F, 1.2F);
                if (owner != null) {
                    owner.displayClientMessage(Component.translatable("tensurafragments.soul.faded", mob.getDisplayName()), true);
                }
                mob.discard();
                return;
            }
        }
        if (owner == null) {
            return;
        }
        // Never you or your allies, checked every tick: brain-driven mobs (most of Tensura's) pick targets into their
        // brain's memory, which the target event below doesn't fully stop.
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
            setBrainTarget(mob, target);
        }
        if (mob.getTarget() == null && mob.distanceToSqr(owner) > 10 * 10 && mob.level() == owner.level()) {
            mob.getNavigation().moveTo(owner, 1.2);
        }
    }

    /** Brain memories that make a mob go for someone. */
    private static final List<MemoryModuleType<? extends LivingEntity>> TARGET_MEMORIES = List.of(
            MemoryModuleType.ATTACK_TARGET, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_ATTACKABLE);

    private static void forgetFriendlyTargets(Mob mob, ServerPlayer owner) {
        if (mob.getTarget() != null && SoulReaper.isFriendly(mob.getTarget(), owner)) {
            mob.setTarget(null);
        }
        Brain<?> brain = mob.getBrain();
        for (MemoryModuleType<? extends LivingEntity> memory : TARGET_MEMORIES) {
            if (brain.checkMemory(memory, MemoryStatus.REGISTERED)
                    && SoulReaper.isFriendly(brain.getMemory(memory).orElse(null), owner)) {
                brain.eraseMemory(memory);
            }
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)
                && brain.getMemory(MemoryModuleType.ANGRY_AT).map(id -> SoulReaper.isFriendly(
                ((ServerLevel) mob.level()).getEntity(id), owner)).orElse(false)) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }

    private static void setBrainTarget(Mob mob, LivingEntity target) {
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
    }

    private static LivingEntity pickTarget(ServerPlayer owner, Mob mob) {
        for (LivingEntity candidate : new LivingEntity[] {owner.getLastHurtMob(), owner.getLastHurtByMob()}) {
            if (candidate != null && candidate.isAlive() && candidate != mob && !SoulReaper.isFriendly(candidate, owner)
                    && candidate.level() == mob.level() && candidate.distanceToSqr(mob) < 32 * 32) {
                return candidate;
            }
        }
        return null;
    }

    /** A soul never turns on its reaper or the reaper's other creatures. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        SoulBond bond = SoulBond.get(event.getEntity());
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (bond != null && target != null && event.getEntity().level() instanceof ServerLevel level
                && level.getPlayerByUUID(bond.owner()) instanceof ServerPlayer owner && SoulReaper.isFriendly(target, owner)) {
            // Cancelled rather than set to nothing, so it keeps going after whatever it was already fighting.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && event.getEntity() instanceof ServerPlayer victim && SoulBond.isBoundTo(attacker, victim)) {
            event.setCanceled(true);
        }
    }

    /** Summoned souls leave nothing behind. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        SoulBond bond = SoulBond.get(event.getEntity());
        if (bond != null && bond.summoned()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        SoulBond bond = SoulBond.get(event.getEntity());
        if (bond != null && bond.summoned()) {
            event.setCanceled(true);
        }
    }

    /** Players who come into range need to know how to draw it. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        SoulBond bond = SoulBond.get(event.getTarget());
        if (bond != null && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SoulEntityPayload(event.getTarget().getId(), bond.kind()));
        }
    }
}
