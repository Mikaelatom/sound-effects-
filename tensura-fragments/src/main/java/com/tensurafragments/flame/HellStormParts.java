package com.tensurafragments.flame;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for the two halves of Draconic Hell Storm (the circle on the hand, and the storm itself), and the
 * storm's shape. The server's hitbox and the client's drawing both use these, so what burns is what you see.
 */
public final class HellStormParts {
    /**
     * Gluttony's mist model (Tensura's gluttony_mist.geo.json, as its loop animation scales it) is a cone about
     * 18.8 blocks long and 8.4 blocks in radius at its far end.
     */
    public static final float MODEL_LENGTH = 150.25F * 2F / 16F;
    public static final float MODEL_END_RADIUS = 18F * 1.5F * 5F / 16F;

    /** The cone's radius along its length, as a share of its far-end radius (the model's body cubes, hand to tip). */
    private static final double[][] PROFILE = {
            {14 / 150.25, 2 / 18.0},
            {28 / 150.25, 5 / 18.0},
            {56 / 150.25, 9 / 18.0},
            {103 / 150.25, 14 / 18.0},
            {1.0, 1.0},
    };

    private HellStormParts() {
    }

    /** Just in front of the caster's right hand, where the magic circle sits and the storm pours from. */
    public static Vec3 handPosition(Vec3 eye, Vec3 look) {
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return eye.add(look.scale(1.2)).add(right.scale(0.35)).add(0, -0.3, 0);
    }

    static Vec3 handPosition(Entity caster) {
        return handPosition(caster.getEyePosition(), caster.getLookAngle());
    }

    /** The storm's radius {@code along} blocks out from the hand, when it reaches {@code length} blocks. */
    public static double radiusAt(double along, double length, double endRadius) {
        double t = length <= 0 ? 1 : along / length;
        for (double[] step : PROFILE) {
            if (t <= step[0]) {
                return endRadius * step[1];
            }
        }
        return endRadius;
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
