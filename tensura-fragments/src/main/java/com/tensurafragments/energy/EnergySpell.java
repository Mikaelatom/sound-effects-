package com.tensurafragments.energy;

import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.BoulderShotProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBoltProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FlameSphereProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FrostBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.IceLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.LightningLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.PlasmaBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.StoneShotProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.ThunderLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WaterBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WaterBladeProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WindBladeProjectile;
import java.util.function.BiFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Tensura's spells as Energy Magic can cast them: each is the normal spell (its usual damage here), cast glowing
 * green at twice the power, paid for in experience levels.
 */
public enum EnergySpell {
    FIRE_BOLT("fire_bolt", FireBoltProjectile::new, 5.0F, 2.5F, 1, 4),
    WIND_BLADE("wind_blade", WindBladeProjectile::new, 5.0F, 2.5F, 1, 0),
    WATER_BLADE("water_blade", WaterBladeProjectile::new, 6.0F, 2.0F, 1, 0),
    STONE_SHOT("stone_shot", StoneShotProjectile::new, 7.0F, 1.8F, 1, 0),
    WATER_BALL("water_ball", WaterBallProjectile::new, 7.0F, 1.6F, 2, 0),
    ICE_LANCE("ice_lance", IceLanceProjectile::new, 7.0F, 2.5F, 2, 0),
    FIRE_BALL("fire_ball", FireBallProjectile::new, 8.0F, 1.6F, 2, 5),
    FROST_BALL("frost_ball", FrostBallProjectile::new, 8.0F, 1.6F, 2, 0),
    LIGHTNING_LANCE("lightning_lance", LightningLanceProjectile::new, 9.0F, 3.0F, 2, 0),
    BOULDER_SHOT("boulder_shot", BoulderShotProjectile::new, 11.0F, 1.4F, 3, 0),
    FIRE_LANCE("fire_lance", FireLanceProjectile::new, 12.0F, 3.0F, 3, 6),
    THUNDER_LANCE("thunder_lance", ThunderLanceProjectile::new, 13.0F, 3.0F, 3, 0),
    FLAME_SPHERE("flame_sphere", FlameSphereProjectile::new, 14.0F, 1.2F, 4, 8),
    PLASMA_BALL("plasma_ball", PlasmaBallProjectile::new, 20.0F, 1.5F, 5, 10);

    private final String id;
    private final BiFunction<Level, LivingEntity, TensuraFlyingProjectile> factory;
    private final float damage;
    private final float speed;
    private final int levels;
    private final int burnSeconds;

    EnergySpell(String id, BiFunction<Level, LivingEntity, TensuraFlyingProjectile> factory, float damage, float speed,
                int levels, int burnSeconds) {
        this.id = id;
        this.factory = factory;
        this.damage = damage;
        this.speed = speed;
        this.levels = levels;
        this.burnSeconds = burnSeconds;
    }

    /** Lower-case name, matching Tensura's spell id (for its name). */
    public String id() {
        return id;
    }

    /** The normal spell's damage; Energy Magic casts it at twice this. */
    public float baseDamage() {
        return damage;
    }

    /** Experience levels it costs, before the config multiplier. */
    public int baseLevels() {
        return levels;
    }

    /** Builds the spell's projectile, aimed from the caster, at {@code power} times its normal damage. */
    public TensuraFlyingProjectile create(LivingEntity caster, float power) {
        TensuraFlyingProjectile projectile = com.tensurafragments.skill.EpScaling.markScaled(factory.apply(caster.level(), caster));
        projectile.setDamage(damage * power * com.tensurafragments.skill.EpScaling.multiplier(caster));
        projectile.setSpeed(speed);
        if (burnSeconds > 0) {
            projectile.setBurnTicks(burnSeconds * 20);
        }
        projectile.setPosAndShoot(caster);
        return projectile;
    }

    public EnergySpell next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static EnergySpell byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
