package com.tensurafragments.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How charged a player's attack is, so a punch held back for its animation lands with the charge it was thrown with. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("attackStrengthTicker")
    int tensurafragments$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void tensurafragments$setAttackStrengthTicker(int ticks);
}
