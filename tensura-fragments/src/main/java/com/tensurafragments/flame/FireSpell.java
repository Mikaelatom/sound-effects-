package com.tensurafragments.flame;

import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.BlackFlameBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBoltProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FlameOrbProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FlameSphereProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.HeatSphereProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.HellFlareProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.MagmaShotProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.PlasmaBallProjectile;
import java.util.function.BiFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Tensura's fire magic, all available to the Flame Emperor. Weakest and cheapest first. */
public enum FireSpell {
    FIRE_BOLT("fire_bolt", FireBoltProjectile::new, 5.0F, 2.5F, 20, 4),
    FIRE_BALL("fire_ball", FireBallProjectile::new, 8.0F, 1.6F, 35, 5),
    MAGMA_SHOT("magma_shot", MagmaShotProjectile::new, 9.0F, 1.8F, 40, 6),
    FIRE_LANCE("fire_lance", FireLanceProjectile::new, 12.0F, 3.0F, 55, 6),
    FLAME_ORB("flame_orb", FlameOrbProjectile::new, 10.0F, 1.2F, 60, 8),
    FLAME_SPHERE("flame_sphere", FlameSphereProjectile::new, 14.0F, 1.2F, 80, 8),
    HEAT_SPHERE("heat_sphere", HeatSphereProjectile::new, 16.0F, 1.0F, 100, 10),
    PLASMA_BALL("plasma_ball", PlasmaBallProjectile::new, 20.0F, 1.5F, 140, 10),
    BLACK_FLAME_BALL("black_flame_ball", BlackFlameBallProjectile::new, 24.0F, 1.5F, 180, 12),
    HELL_FLARE("hell_flare", HellFlareProjectile::new, 30.0F, 1.2F, 250, 15);

    private final String id;
    private final BiFunction<Level, ServerPlayer, TensuraFlyingProjectile> factory;
    private final float damage;
    private final float speed;
    private final double magiculeCost;
    private final int burnSeconds;

    FireSpell(String id, BiFunction<Level, ServerPlayer, TensuraFlyingProjectile> factory, float damage, float speed,
              double magiculeCost, int burnSeconds) {
        this.id = id;
        this.factory = factory;
        this.damage = damage;
        this.speed = speed;
        this.magiculeCost = magiculeCost;
        this.burnSeconds = burnSeconds;
    }

    public String id() {
        return id;
    }

    public double magiculeCost() {
        return magiculeCost;
    }

    /** Builds Tensura's projectile for this spell, aimed and positioned from the caster. */
    public TensuraFlyingProjectile create(ServerPlayer caster) {
        TensuraFlyingProjectile projectile = factory.apply(caster.level(), caster);
        projectile.setDamage(damage * com.tensurafragments.skill.EpScaling.multiplier(caster));
        projectile.setSpeed(speed);
        projectile.setBurnTicks(burnSeconds * 20);
        projectile.setPosAndShoot(caster);
        return projectile;
    }

    public FireSpell next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static FireSpell byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
