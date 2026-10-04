package com.tensurafragments.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.ArcPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws Combat Mode's arcs as solid white pixels: a run of small white cubes packed edge to edge along the arc, thickest
 * in the middle. A sweeping arc is drawn from its first point to its last and then cut away behind; a still one just
 * shrinks away at the end.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class ArcRenderer {
    /** Spacing of the cubes along an arc (smaller than any cube, so they overlap into one solid line). */
    private static final double STEP = 0.035;

    private record Arc(List<Vec3> samples, float thickness, int life, boolean sweep, long born) {
    }

    private static final List<Arc> ARCS = new ArrayList<>();

    private ArcRenderer() {
    }

    public static void add(ArcPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || payload.points().size() < 2) {
            return;
        }
        ARCS.add(new Arc(resample(payload.points()), payload.thickness(), Math.max(1, payload.life()), payload.sweep(),
                mc.level.getGameTime()));
        if (ARCS.size() > 64) {
            ARCS.remove(0);
        }
    }

    /** Evenly spaced points along the polyline. */
    private static List<Vec3> resample(List<Vec3> points) {
        List<Vec3> samples = new ArrayList<>();
        samples.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);
            int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / STEP));
            for (int s = 1; s <= steps; s++) {
                samples.add(from.lerp(to, s / (double) steps));
            }
        }
        return samples;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || ARCS.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = stack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RenderType.debugQuads());
        Iterator<Arc> it = ARCS.iterator();
        while (it.hasNext()) {
            Arc arc = it.next();
            double age = (now - arc.born()) / arc.life();
            if (age >= 1 || age < -0.5) {
                it.remove();
                continue;
            }
            age = Math.max(0, age);
            // Sweeping: the head runs along the arc in the first half, the tail follows in the second.
            double head = arc.sweep() ? Math.min(1, age * 2.2) : 1;
            double tail = arc.sweep() ? Math.max(0, age * 2.2 - 1.2) : 0;
            double shrink = arc.sweep() ? 1 : Math.min(1, (1 - age) * 3);
            int count = arc.samples().size();
            for (int i = 0; i < count; i++) {
                double t = i / (double) (count - 1);
                if (t < tail || t > head) {
                    continue;
                }
                double taper = 0.35 + 0.65 * Math.sin(t * Math.PI);
                float half = (float) (arc.thickness() * 0.5 * taper * shrink);
                if (half > 0.004F) {
                    cube(out, matrix, arc.samples().get(i), half);
                }
            }
        }
        buffers.endBatch(RenderType.debugQuads());
        stack.popPose();
    }

    /** A solid white cube. */
    private static void cube(VertexConsumer out, Matrix4f m, Vec3 c, float h) {
        float x0 = (float) c.x - h;
        float y0 = (float) c.y - h;
        float z0 = (float) c.z - h;
        float x1 = (float) c.x + h;
        float y1 = (float) c.y + h;
        float z1 = (float) c.z + h;
        quad(out, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(out, m, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        quad(out, m, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(out, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(out, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(out, m, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
    }

    private static void quad(VertexConsumer out, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        out.addVertex(m, ax, ay, az).setColor(255, 255, 255, 255);
        out.addVertex(m, bx, by, bz).setColor(255, 255, 255, 255);
        out.addVertex(m, cx, cy, cz).setColor(255, 255, 255, 255);
        out.addVertex(m, dx, dy, dz).setColor(255, 255, 255, 255);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ARCS.clear();
    }
}
