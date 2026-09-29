package com.tensurafragments.flame;

import com.tensurafragments.Config;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Draconic Hellfire: the burn left by Draconic Hell Storm. It never goes out. Water, milk, totems and fire
 * resistance don't stop it; it keeps the target alight and burning until they die.
 */
public class DraconicHellfireEffect extends MobEffect {
    public DraconicHellfireEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF3A10);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Relit every tick, so even water can't keep it out.
        entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 40));
        if (entity.tickCount % 20 == 0) {
            entity.invulnerableTime = 0;
            entity.hurt(HellfireDamage.burn(entity),
                    Config.HELLFIRE_DAMAGE_PER_SECOND.get().floatValue() * (amplifier + 1));
        }
        if (entity.level() instanceof ServerLevel level && entity.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6,
                    entity.getZ(), 2, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.01);
        }
        return true;
    }

    /** No cures: not milk, not a totem, nothing. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
    }
}
