package com.tensurafragments.flame;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for the two halves of Draconic Hell Storm (the circle on the hand, and the storm itself). */
final class HellStormParts {
    private HellStormParts() {
    }

    /** Just in front of the caster's right hand, where the magic circle sits and the storm pours from. */
    static Vec3 handPosition(Entity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return caster.getEyePosition().add(look.scale(1.2)).add(right.scale(0.35)).add(0, -0.3, 0);
    }

    @Nullable
    static ServerPlayer caster(Entity part, int casterId) {
        return part.level().getEntity(casterId) instanceof ServerPlayer player && player.isAlive() ? player : null;
    }

    /** Points the part the way the caster is looking. */
    static void follow(Entity part, Entity caster) {
        Vec3 at = handPosition(caster);
        part.setPos(at.x, at.y, at.z);
        part.setYRot(caster.getYRot());
        part.setXRot(caster.getXRot());
        part.yRotO = part.getYRot();
        part.xRotO = part.getXRot();
    }
}
