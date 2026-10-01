package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Wraps the buffers an entity renders into so it comes out in one glowing colour: every vertex colour becomes the tint
 * (keeping its brightness) and is lit at full brightness. Works for any renderer, Tensura's spells included. Used for
 * energy spells (green) and summoned souls (soul blue).
 */
public final class TintBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final int red;
    private final int green;
    private final int blue;

    public TintBufferSource(MultiBufferSource delegate, int rgb) {
        this.delegate = delegate;
        this.red = (rgb >> 16) & 0xFF;
        this.green = (rgb >> 8) & 0xFF;
        this.blue = rgb & 0xFF;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return new Tinting(delegate.getBuffer(renderType), this);
    }

    private static final class Tinting implements VertexConsumer {
        private final VertexConsumer delegate;
        private final TintBufferSource tint;

        Tinting(VertexConsumer delegate, TintBufferSource tint) {
            this.delegate = delegate;
            this.tint = tint;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            // The tint, brighter where the original was bright, never dark.
            float brightness = 0.55F + 0.45F * Math.max(red, Math.max(green, blue)) / 255F;
            delegate.setColor(Math.round(tint.red * brightness), Math.round(tint.green * brightness),
                    Math.round(tint.blue * brightness), alpha);
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
