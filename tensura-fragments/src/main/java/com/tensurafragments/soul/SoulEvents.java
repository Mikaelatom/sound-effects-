package com.tensurafragments.soul;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ally.Allies;
import com.tensurafragments.ally.Companions;
import com.tensurafragments.network.SoulEntityPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
            // A named soul stays for good (it waits while you're away).
            if (!Companions.isNamed(mob) && (owner == null || !owner.isAlive() || level.getGameTime() >= bond.until())) {
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
        if (owner != null && owner.level() == level) {
            Allies.serve(mob, owner);
        }
    }

    /** A soul only ever sets its sights on what its reaper hit. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        SoulBond bond = SoulBond.get(event.getEntity());
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (bond != null && target != null && event.getEntity().level() instanceof ServerLevel level
                && level.getPlayerByUUID(bond.owner()) instanceof ServerPlayer owner && !Allies.mayTarget(target, owner)) {
            // Only what its owner hit; cancelled (not set to nothing) so it keeps after that.
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
