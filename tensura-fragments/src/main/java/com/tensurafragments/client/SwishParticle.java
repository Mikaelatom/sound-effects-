package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * The punch swish from the Punch Swish mod: an 8-frame white swoosh that hangs in front of the punch for 8 ticks. Its
 * "speed" carries its settings instead: x the roll (radians), y the size, z whether it's mirrored (a left-hand swish).
 * Once spawned for a punch it's turned to face the way the puncher faces ({@link #facing}), so from any angle it points
 * along the punch instead of turning toward the camera like an ordinary particle.
 */
public class SwishParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean mirror;
    /** The puncher's view when it was thrown (yaw, pitch in degrees); null: face the camera. */
    private float[] facing;

    protected SwishParticle(ClientLevel level, double x, double y, double z, double roll, double size, double mirror,
                            SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.mirror = mirror > 0.5;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.gravity = 0;
        this.hasPhysics = false;
        this.lifetime = 8;
        this.quadSize = (float) size;
        this.roll = (float) roll;
        this.oRoll = this.roll;
        setSpriteFromAge(sprites);
    }

    /**
     * Laid back from facing the puncher's eyes (60 degrees), so the swoosh sweeps out forward in front of the fist
     * instead of standing up across the view.
     */
    private static final float TILT = (float) Math.toRadians(-60);

    /** Points the swish the way the puncher faces. */
    public void face(float yaw, float pitch) {
        facing = new float[] {yaw, pitch};
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (facing == null) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        // Exactly how it looks from the puncher's own eyes (as a camera there would turn it), fixed in the world.
        Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.PI - facing[0] * Mth.DEG_TO_RAD,
                -facing[1] * Mth.DEG_TO_RAD, 0).rotateX(TILT).rotateZ(Mth.lerp(partialTicks, oRoll, roll));
        renderRotatedQuad(buffer, camera, rotation, partialTicks);
        // And its back, so it shows from in front of the puncher too.
        renderRotatedQuad(buffer, camera, new Quaternionf(rotation).rotateY((float) Math.PI), partialTicks);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
        } else {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    protected float getU0() {
        return mirror ? super.getU1() : super.getU0();
    }

    @Override
    protected float getU1() {
        return mirror ? super.getU0() : super.getU1();
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd,
                                       double yd, double zd) {
            return new SwishParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
