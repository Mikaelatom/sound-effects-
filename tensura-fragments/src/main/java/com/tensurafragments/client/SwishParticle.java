package com.tensurafragments.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The punch swish from the Punch Swish mod: an 8-frame white swoosh that hangs in front of the punch for 8 ticks. Its
 * "speed" carries its settings instead: x the roll (radians), y the size, z whether it's mirrored (a left-hand swish).
 */
public class SwishParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean mirror;

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
