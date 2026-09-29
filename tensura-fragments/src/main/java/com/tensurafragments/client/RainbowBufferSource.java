package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Wraps the buffers an entity renders into so every vertex colour is multiplied by a rainbow that sweeps across the
 * model. Works for any renderer (including Tensura's GeckoLib spells) since it only changes the colour it writes.
 */
public final class RainbowBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float baseHue;

    public RainbowBufferSource(MultiBufferSource delegate, float baseHue) {
        this.delegate = delegate;
        this.baseHue = baseHue;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return new Tinting(delegate.getBuffer(renderType), baseHue);
    }

    private static final class Tinting implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float baseHue;
        private float lastX;
        private float lastY;
        private float lastZ;

        Tinting(VertexConsumer delegate, float baseHue) {
            this.delegate = delegate;
            this.baseHue = baseHue;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            lastX = x;
            lastY = y;
            lastZ = z;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            // Bands of colour across the model, shifting over time. Keep the original brightness.
            float hue = (baseHue + (lastX + lastY + lastZ) * 0.35F) % 1F;
            int rgb = Mth.hsvToRgb(hue < 0 ? hue + 1 : hue, 0.75F, 1.0F);
            float brightness = Math.max(red, Math.max(green, blue)) / 255F;
            delegate.setColor(Math.round(((rgb >> 16) & 0xFF) * brightness), Math.round(((rgb >> 8) & 0xFF) * brightness),
                    Math.round((rgb & 0xFF) * brightness), alpha);
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
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
