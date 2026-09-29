package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensurafragments.flame.HellStormParts;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Pins a Hell Storm part to its caster's hand every frame, from the caster's smoothed position and aim, so it never
 * trails behind the synced entity while the caster moves or turns.
 */
final class HandAnchor {
    float yaw;
    float pitch;

    /** Moves the pose to the caster's hand and records the caster's aim; falls back to the part's own when unknown. */
    void pin(Entity part, int casterId, float partialTick, PoseStack poseStack) {
        Entity caster = part.level().getEntity(casterId);
        if (caster == null) {
            yaw = Mth.rotLerp(partialTick, part.yRotO, part.getYRot());
            pitch = Mth.lerp(partialTick, part.xRotO, part.getXRot());
            return;
        }
        yaw = caster.getViewYRot(partialTick);
        pitch = caster.getViewXRot(partialTick);
        Vec3 hand = HellStormParts.handPosition(caster.getEyePosition(partialTick), caster.getViewVector(partialTick));
        Vec3 drawnAt = part.getPosition(partialTick);
        poseStack.translate(hand.x - drawnAt.x, hand.y - drawnAt.y, hand.z - drawnAt.z);
    }
}
