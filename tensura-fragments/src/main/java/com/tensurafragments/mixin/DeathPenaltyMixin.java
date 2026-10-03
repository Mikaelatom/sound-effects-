package com.tensurafragments.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.manasmods.tensura.storage.ep.ExistenceStorage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Players keep their max EP when they die (Tensura takes {@code epDeathPenalty} percent of it). Its other death
 * penalty, the soul point cut, still applies.
 */
@Mixin(value = ExistenceStorage.class, remap = false)
public abstract class DeathPenaltyMixin {
    @WrapOperation(method = "applyDeathPenalty", at = @At(value = "INVOKE",
            target = "Lio/github/manasmods/tensura/util/EnergyHelper;multiplyMaxEP(Lnet/minecraft/world/entity/LivingEntity;D)V"))
    private static void tensurafragments$keepEp(LivingEntity entity, double multiplier, Operation<Void> original) {
        if (!(entity instanceof Player)) {
            original.call(entity, multiplier);
        }
    }
}
