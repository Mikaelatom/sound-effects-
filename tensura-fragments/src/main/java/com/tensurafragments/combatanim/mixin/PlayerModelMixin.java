package com.tensurafragments.combatanim.mixin;

import com.tensurafragments.combatanim.CombatAnimations;
import com.tensurafragments.combatanim.client.CombatAnimator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    /**
     * Vanilla never resets some fields (head/body x, z and zRot, leg x), so without this an animation's
     * last pose sticks to the head and body after it ends. The model is shared by every player, so it
     * would also leak onto other players.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void combatanim$reset(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        this.head.resetPose();
        this.body.resetPose();
        this.rightArm.resetPose();
        this.leftArm.resetPose();
        this.rightLeg.resetPose();
        this.leftLeg.resetPose();
    }

    /** Runs after vanilla posing, so the animation overrides the arm swing and walk cycle while it plays. */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void combatanim$apply(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        CombatAnimator.Pose pose = CombatAnimator.sample(player, ageInTicks);
        if (pose == null) return;

        pose.apply(this.head, CombatAnimations.HEAD, true);
        pose.apply(this.body, CombatAnimations.BODY, false);
        pose.apply(this.rightArm, CombatAnimations.RIGHT_ARM, false);
        pose.apply(this.leftArm, CombatAnimations.LEFT_ARM, false);
        // Tensura: Fragments - in the guard, punching, blocking or holding, the legs keep walking while you move.
        CombatAnimator.Pose legs = pose;
        String name = CombatAnimator.current(player);
        if (name != null && (name.equals("idle_guard") || name.startsWith("punch_") || name.equals("block")
                || name.equals("brawler_hold"))) {
            float walking = net.minecraft.util.Mth.clamp(limbSwingAmount * 1.5F, 0F, 1F);
            legs = new CombatAnimator.Pose(pose.anim(), pose.seconds(), pose.weight() * (1F - walking));
        }
        legs.apply(this.rightLeg, CombatAnimations.RIGHT_LEG, false);
        legs.apply(this.leftLeg, CombatAnimations.LEFT_LEG, false);

        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }
}
