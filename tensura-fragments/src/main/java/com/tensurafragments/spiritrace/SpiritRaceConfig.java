package com.tensurafragments.spiritrace;

import io.github.manasmods.tensura.config.race.RaceConfig;

/** A spirit stage's base stats, in Tensura's race-config shape (modelled on the Daemon line at the same stage). */
final class SpiritRaceConfig extends RaceConfig.Default {
    private final double minAura;
    private final double maxAura;
    private final double minMagicule;
    private final double maxMagicule;
    private final double maxHealth;
    private final double maxSpiritualHealth;
    private final double attack;
    private final double attackSpeed;
    private final double knockbackResistance;
    private final double movementSpeed;

    SpiritRaceConfig(double minAura, double maxAura, double minMagicule, double maxMagicule, double maxHealth,
                     double maxSpiritualHealth, double attack, double attackSpeed, double knockbackResistance,
                     double movementSpeed) {
        this.minAura = minAura;
        this.maxAura = maxAura;
        this.minMagicule = minMagicule;
        this.maxMagicule = maxMagicule;
        this.maxHealth = maxHealth;
        this.maxSpiritualHealth = maxSpiritualHealth;
        this.attack = attack;
        this.attackSpeed = attackSpeed;
        this.knockbackResistance = knockbackResistance;
        this.movementSpeed = movementSpeed;
    }

    @Override
    public double getMinAura() {
        return minAura;
    }

    @Override
    public double getMaxAura() {
        return maxAura;
    }

    @Override
    public double getMinMagicule() {
        return minMagicule;
    }

    @Override
    public double getMaxMagicule() {
        return maxMagicule;
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public double getMaxHealth() {
        return maxHealth;
    }

    @Override
    public double getMaxSpiritualHealth() {
        return maxSpiritualHealth;
    }

    @Override
    public double getAttack() {
        return attack;
    }

    @Override
    public double getAttackSpeed() {
        return attackSpeed;
    }

    @Override
    public double getKnockbackResistance() {
        return knockbackResistance;
    }

    @Override
    public double getMovementSpeed() {
        return movementSpeed;
    }

    @Override
    public double getSwimSpeed() {
        return 0;
    }
}
