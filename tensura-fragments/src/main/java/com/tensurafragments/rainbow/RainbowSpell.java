package com.tensurafragments.rainbow;

import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.IceLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.LightningLanceProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.StoneShotProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WaterBladeProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WindBladeProjectile;
import java.util.function.BiFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** The Tensura spells Rainbow Magic can cast, each recoloured in rainbow and striking with every element. */
public enum RainbowSpell {
    FIRE_BALL("fire_ball", FireBallProjectile::new, 6.0F, 1.5F, 30),
    WATER_BLADE("water_blade", WaterBladeProjectile::new, 6.0F, 2.0F, 30),
    WIND_BLADE("wind_blade", WindBladeProjectile::new, 5.0F, 2.5F, 25),
    LIGHTNING_LANCE("lightning_lance", LightningLanceProjectile::new, 9.0F, 3.0F, 45),
    STONE_SHOT("stone_shot", StoneShotProjectile::new, 7.0F, 1.8F, 30),
    ICE_LANCE("ice_lance", IceLanceProjectile::new, 7.0F, 2.5F, 35);

    private final String id;
    private final BiFunction<Level, ServerPlayer, TensuraFlyingProjectile> factory;
    private final float damage;
    private final float speed;
    private final double magiculeCost;

    RainbowSpell(String id, BiFunction<Level, ServerPlayer, TensuraFlyingProjectile> factory, float damage, float speed,
                 double magiculeCost) {
        this.id = id;
        this.factory = factory;
        this.damage = damage;
        this.speed = speed;
        this.magiculeCost = magiculeCost;
    }

    /** Lower-case name, used for translation keys. */
    public String id() {
        return id;
    }

    /** Builds Tensura's projectile for this spell, aimed and positioned from the caster. */
    public TensuraFlyingProjectile create(ServerPlayer caster) {
        TensuraFlyingProjectile projectile = factory.apply(caster.level(), caster);
        projectile.setDamage(damage);
        projectile.setSpeed(speed);
        projectile.setPosAndShoot(caster);
        return projectile;
    }

    /** Before the rainbow multiplier. */
    public double baseMagiculeCost() {
        return magiculeCost;
    }

    public RainbowSpell next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RainbowSpell byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
