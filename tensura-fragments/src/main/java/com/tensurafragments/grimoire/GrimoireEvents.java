package com.tensurafragments.grimoire;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ally.Allies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Keeps released creatures loyal, sends them back to the book when their time is up, and runs magic catching. */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class GrimoireEvents {
    private GrimoireEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SealingGrimoire.tickCatch(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.hasData(com.tensurafragments.ModRegistries.BINDING)) {
            return;
        }
        Binding binding = Binding.get(entity);
        ServerPlayer binder = level.getPlayerByUUID(binding.binder()) instanceof ServerPlayer p ? p : null;
        if (binder == null || !binder.isAlive() || level.getGameTime() >= binding.until()) {
            // Time's up (or the binder is gone): back into the book, or it fades away if there's no room.
            if (binder == null || !SealingGrimoire.reseal(entity, binder)) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5, entity.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                if (binder != null) {
                    binder.displayClientMessage(Component.translatable("tensurafragments.grimoire.faded", entity.getDisplayName()), true);
                }
                entity.discard();
            }
            return;
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (entity.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ(),
                    3, 0.2, 0.1, 0.2, 0.3);
        }
        Allies.serve(mob, binder);
    }

    /** A released creature never turns on its binder or the binder's other creatures. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        Binding binding = Binding.get(event.getEntity());
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (binding != null && target != null && event.getEntity().level() instanceof ServerLevel level
                && level.getPlayerByUUID(binding.binder()) instanceof ServerPlayer binder
                && Allies.isFriendly(target, binder)) {
            // Cancelled rather than set to nothing, so it keeps going after whatever it was already fighting.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && event.getEntity() instanceof ServerPlayer victim && Binding.isBoundTo(attacker, victim)) {
            event.setCanceled(true);
        }
    }
}
