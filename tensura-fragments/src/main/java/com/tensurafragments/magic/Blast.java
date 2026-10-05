package com.tensurafragments.magic;

import com.tensurafragments.card.CardEntity;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The shared magic blast used by cards and paper talismans. It hurts the caster too if they're close. */
public final class Blast {
    private Blast() {
    }

    /**
     * @param power multiplies damage and knockback (a card's charge, 1 for talismans)
     */
    public static void explode(ServerLevel level, Entity source, @Nullable Entity owner, Vec3 centre, double radius,
                               float damage, double knockback, float selfDamageMultiplier, float power) {
        DamageSource hostile = level.damageSources().explosion(source, owner);
        // Self damage has no attacker, so it still applies on servers with PvP turned off.
        DamageSource self = level.damageSources().explosion(source, null);

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius))) {
            Vec3 body = victim.position().add(0, victim.getBbHeight() / 2.0, 0);
            double distance = body.distanceTo(centre);
            if (distance > radius) {
                continue;
            }
            float falloff = (float) (1.0 - distance / radius);
            float amount = damage * power * falloff * com.tensurafragments.skill.EpScaling.multiplier(owner);
            if (victim == owner) {
                amount *= selfDamageMultiplier;
            }
            if (amount > 0) {
                victim.hurt(victim == owner ? self : hostile, amount);
            }

            Vec3 push = body.subtract(centre);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : push.normalize();
            push = push.scale(knockback * falloff * power);
            victim.push(push.x, push.y + 0.2 * falloff, push.z);
            if (victim instanceof ServerPlayer player) {
                player.hurtMarked = true;
            }
        }

        // Cards caught in any blast go off a few ticks later.
        for (CardEntity card : level.getEntitiesOfClass(CardEntity.class, new AABB(centre, centre).inflate(radius))) {
            if (card != source && card.position().distanceTo(centre) <= radius) {
                card.prime(4);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 1 + Math.round(power * 2), 0.5, 0.5, 0.5, 0);
        // Tensura's shockwave ring, sized to the blast.
        TensuraParticleHelper.spawnServerParticles(level,
                TensuraParticleUtils.getColorlessReversedWave(0.9F, (float) radius), centre.x, centre.y, centre.z);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, centre.x, centre.y, centre.z, 30, 0.3, 0.3, 0.3, 0.6);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.0F + power * 0.5F, 1.2F);
    }
}
