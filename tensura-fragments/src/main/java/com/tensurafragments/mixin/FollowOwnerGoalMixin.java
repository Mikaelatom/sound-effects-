package com.tensurafragments.mixin;

import com.tensurafragments.ally.Companions;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A named companion told to stay or wander doesn't follow (or teleport to) its owner. */
@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin {
    @Shadow
    @Final
    private TamableAnimal tamable;

    @Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
    private void tensurafragments$onlyWhenFollowing(CallbackInfoReturnable<Boolean> cir) {
        if (Companions.mode(tamable) != Companions.FOLLOW) {
            cir.setReturnValue(false);
        }
    }
}
