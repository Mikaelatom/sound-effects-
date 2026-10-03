package com.tensurafragments.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tensurafragments.ally.Companions;
import io.github.manasmods.tensura.network.c2s.RequestNamingKeyPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tensura's Naming works on any creature, not just the ones on its {@code can_be_named} list (its other rules stay: the
 * creature has to submit, you need the EP, and it can't already have a name). Summons are the exception both ways: your
 * own and your allies' always submit and need no more EP than yours, and nobody else can name them.
 */
@Mixin(value = RequestNamingKeyPacket.class, remap = false)
public abstract class TensuraNamingMixin {
    @WrapOperation(method = "isNotNameable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;is(Lnet/minecraft/tags/TagKey;)Z"))
    private static boolean tensurafragments$anyCreature(EntityType<?> type, TagKey<EntityType<?>> tag,
                                                        Operation<Boolean> original) {
        return true;
    }

    @Inject(method = "isNotNameable", at = @At("HEAD"), cancellable = true)
    private static void tensurafragments$summons(LivingEntity target, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (target.isAlive() && !(target instanceof Player) && Companions.ownerOf(target) != null) {
            cir.setReturnValue(!(player instanceof ServerPlayer serverPlayer && Companions.mayName(serverPlayer, target)));
        }
    }

    /** You don't need more EP than your own (or your ally's) summon to name it; the naming still costs magicules. */
    @WrapOperation(method = "canName", at = @At(value = "INVOKE",
            target = "Lio/github/manasmods/tensura/util/EnergyHelper;getMaxEP(Lnet/minecraft/world/entity/LivingEntity;)D"))
    private static double tensurafragments$summonEp(LivingEntity target, Operation<Double> original,
                                                    @Local(argsOnly = true) Player player) {
        if (player instanceof ServerPlayer serverPlayer && Companions.ownerOf(target) != null
                && Companions.mayName(serverPlayer, target)) {
            return 0;
        }
        return original.call(target);
    }

    @Inject(method = "isNotSubmitting", at = @At("HEAD"), cancellable = true)
    private static void tensurafragments$summonsSubmit(LivingEntity target, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer serverPlayer && Companions.ownerOf(target) != null
                && Companions.mayName(serverPlayer, target)) {
            cir.setReturnValue(false);
        }
    }
}
