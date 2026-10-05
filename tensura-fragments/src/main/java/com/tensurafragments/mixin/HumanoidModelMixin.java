package com.tensurafragments.mixin;

import com.tensurafragments.client.CombatPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Combat Mode's blocking and grabbing arms, laid over a player's usual animation (sleeves copy the arms after this). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    /**
     * Players share one model, and the usual animation never puts back some of what the boxing stance moves (the head's
     * and body's roll, the arms' positions). Start every player's frame from the default pose, so nothing carries over
     * from the last frame or from another player.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void tensurafragments$resetPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            model.head.resetPose();
            model.hat.resetPose();
            model.body.resetPose();
            model.rightArm.resetPose();
            model.leftArm.resetPose();
            model.rightLeg.resetPose();
            model.leftLeg.resetPose();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tensurafragments$combatPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        com.tensurafragments.client.BoxingAnimator.apply((HumanoidModel<?>) (Object) this, entity, ageInTicks, limbSwingAmount);
        CombatPoses.apply((HumanoidModel<?>) (Object) this, entity, ageInTicks);
    }
}
