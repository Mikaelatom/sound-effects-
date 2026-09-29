package com.tensurafragments.spirit;

import net.minecraft.resources.ResourceLocation;

/**
 * The spirits Spirit Control calls, in order: each attack brings the next one. They're Tensura's own spirits
 * (models, textures and attack animations), each doing one of its attacks before it fades.
 */
public enum SpiritKind {
    /** Hurls one of Tensura's fire balls from its hand. */
    IFRIT("ifrit", "fire_ball_right", 9, 22, 0.9F, false, 1.0F),
    /** Throws one of Tensura's wind blades. */
    SYLPHIDE("sylphide", "wind_blade_right", 10, 24, 1.0F, false, 1.0F),
    /** Throws one of Tensura's water balls. */
    UNDINE("undine", "water_ball_right", 9, 22, 1.0F, false, 1.0F),
    /** Rises beside the target and stomps: the ground bursts and throws everything near it into the air. */
    WAR_GNOME("war_gnome", "stomp", 12, 28, 0.55F, true, 1.2F),
    /** Pounces through the target from a few blocks away, cutting everything on the way. */
    BLADE_TIGER("blade_tiger", "strike", 8, 22, 0.5F, true, 1.4F);

    private final String id;
    private final String attack;
    private final int strikeTick;
    private final int lifetime;
    private final float scale;
    private final boolean melee;
    private final float damageMultiplier;

    SpiritKind(String id, String attack, int strikeTick, int lifetime, float scale, boolean melee, float damageMultiplier) {
        this.id = id;
        this.attack = attack;
        this.strikeTick = strikeTick;
        this.lifetime = lifetime;
        this.scale = scale;
        this.melee = melee;
        this.damageMultiplier = damageMultiplier;
    }

    public String id() {
        return id;
    }

    public ResourceLocation model() {
        return ResourceLocation.fromNamespaceAndPath("tensura", "geo/entity/" + id + ".geo.json");
    }

    public ResourceLocation animations() {
        return ResourceLocation.fromNamespaceAndPath("tensura", "animations/entity/" + id + ".animation.json");
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/" + id + "/" + id + ".png");
    }

    public String attackAnimation() {
        return "animation." + id + "." + attack;
    }

    /** The tick of its animation where the attack actually happens. */
    public int strikeTick() {
        return strikeTick;
    }

    public int lifetime() {
        return lifetime;
    }

    public float scale() {
        return scale;
    }

    /** Melee spirits appear next to the target; the others appear beside you and throw their magic. */
    public boolean melee() {
        return melee;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public SpiritKind next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SpiritKind byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
