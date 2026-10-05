package com.tensurafragments.combat;

import com.tensurafragments.Config;

/**
 * Combat Mode's fighting styles. Each changes how hard and fast you punch and what your special moves are:
 * <ul>
 * <li>jump + attack: Brawler uppercut, Swift spin kick, Titan hammer fist, Ki palm</li>
 * <li>sneak + attack in mid-air: Brawler down slam, Swift dive kick, Titan meteor slam, Ki bomb</li>
 * <li>grab key: Brawler grab and throw, Swift counter stance, Titan tackle, Ki burst</li>
 * <li>dash key: Brawler dash, Swift quick step, Titan iron body, Ki vanish</li>
 * <li>the finisher: Brawler launch, Swift whirlwind, Titan ground pound, Ki blast</li>
 * </ul>
 */
public enum FightingStyle {
    BRAWLER("brawler", 0, 1.0F, 0.25F, 12, 0),
    SWIFT("swift", 1, 0.8F, 0.2F, 8, 1.0),
    TITAN("titan", -1, 1.35F, 0.6F, 18, -0.6),
    KI("ki", 0, 0.9F, 0.3F, 11, 0);

    private final String id;
    private final int finisherOffset;
    private final float damage;
    private final float knockback;
    private final float downPerHit;
    private final double attackSpeed;

    FightingStyle(String id, int finisherOffset, float damage, float knockback, float downPerHit, double attackSpeed) {
        this.id = id;
        this.finisherOffset = finisherOffset;
        this.damage = damage;
        this.knockback = knockback;
        this.downPerHit = downPerHit;
        this.attackSpeed = attackSpeed;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "tensurafragments.combat.style." + id;
    }

    /** Which hit of a combo is this style's finisher (Swift's comes a hit later, Titan's a hit sooner). */
    public int finisherHit() {
        return Math.max(2, Config.COMBAT_FINISHER_HIT.get() + finisherOffset);
    }

    /** Punch damage, times this. */
    public float damage() {
        return damage;
    }

    /** How much of a punch's usual knockback combo hits keep. */
    public float knockback() {
        return knockback;
    }

    /** Down power each ordinary punch adds to what it hits. */
    public float downPerHit() {
        return downPerHit;
    }

    /** Added to your attack speed while fighting in this style. */
    public double attackSpeed() {
        return attackSpeed;
    }

    public FightingStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static FightingStyle byId(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : BRAWLER;
    }

    public static FightingStyle byName(String name) {
        for (FightingStyle style : values()) {
            if (style.id.equalsIgnoreCase(name)) {
                return style;
            }
        }
        return null;
    }
}
