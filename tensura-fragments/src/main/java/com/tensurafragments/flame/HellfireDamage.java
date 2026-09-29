package com.tensurafragments.flame;

import com.tensurafragments.TensuraFragments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Draconic Hell Storm's own fire damage (data/tensurafragments/damage_type). Deliberately not tagged as vanilla fire,
 * and its message ids avoid the words Tensura uses to spot fire damage ("fire", "flame", "burn", "blaze"...), which
 * fire-immune targets ignore and flame resistance shrinks. So nothing shrugs it off; it's also tagged to get past
 * armour, shields, Resistance, enchantments and Tensura's dodges and barriers. The flames are drawn by setting the
 * target alight.
 */
public final class HellfireDamage {
    /** The storm itself. */
    public static final ResourceKey<DamageType> STORM =
            ResourceKey.create(Registries.DAMAGE_TYPE, TensuraFragments.id("draconic_hellfire"));
    /** The burn it leaves behind. */
    public static final ResourceKey<DamageType> BURN =
            ResourceKey.create(Registries.DAMAGE_TYPE, TensuraFragments.id("hellfire_burn"));

    private HellfireDamage() {
    }

    public static DamageSource storm(Entity storm, @Nullable Entity caster) {
        return storm.damageSources().source(STORM, storm, caster);
    }

    public static DamageSource burn(Entity target) {
        return target.damageSources().source(BURN);
    }
}
