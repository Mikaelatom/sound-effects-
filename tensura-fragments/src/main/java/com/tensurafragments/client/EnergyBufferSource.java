package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Wraps the buffers an entity renders into so it comes out glowing green: every vertex colour becomes energy green
 * (keeping its brightness) and is lit at full brightness. Works for any renderer, Tensura's spells included.
 */
public final class EnergyBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;

    public EnergyBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return new Tinting(delegate.getBuffer(renderType));
    }

    private static final class Tinting implements VertexConsumer {
        private final VertexConsumer delegate;

        Tinting(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            // Energy green, brighter where the original was bright, never dark.
            float brightness = 0.55F + 0.45F * Math.max(red, Math.max(green, blue)) / 255F;
            delegate.setColor(Math.round(110 * brightness), Math.round(255 * brightness), Math.round(90 * brightness), alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            // Glowing: always fully lit.
            delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
