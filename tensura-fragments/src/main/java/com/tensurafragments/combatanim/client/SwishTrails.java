package com.tensurafragments.combatanim.client;

import com.tensurafragments.combatanim.CombatAnim;
import com.tensurafragments.combatanim.CombatAnimations;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The effects exactly as modelled in Blockbench: flat textured planes that pop on and off in sequence.
 * <ul>
 *   <li>Punch swish trails (player_punches.bbmodel): hung off the arm (jabs) or body (hooks, uppercut),
 *       following the animated player model. Data combat_animations/swish_trails.json, texture
 *       textures/entity/combat_swish.png.</li>
 *   <li>Slam shockwaves (player_moves.bbmodel): flat rings on the ground at the player's feet.
 *       Data combat_animations/shockwave.json, texture textures/entity/combat_shockwave.png.</li>
 * </ul>
 * Both JSON files are exported from the bbmodels with SwishExport.java. Don't edit them by hand.
 */
public final class SwishTrails {
    /** Animation name and time into it, in seconds. */
    public record Time(String animation, float seconds) {}

    /** GROUND = model space, no part transform: stays on the ground under the player. */
    private enum Part { BODY, RIGHT_ARM, LEFT_ARM, GROUND }

    private record Quad(float[][] v, float[][] uv, float[] n) {}

    private record Segment(Part part, ResourceLocation texture, List<Quad> quads) {}

    /** A segment is shown from {@code on} (inclusive) to {@code off} (exclusive), in seconds. */
    private record Window(Segment segment, float on, float off) {}

    private static final Map<String, List<Window>> WINDOWS = new HashMap<>();

    private SwishTrails() {}

    public static void load() {
        load("swish_trails.json", "combat_swish.png");
        load("shockwave.json", "combat_shockwave.png");
    }

    private static void load(String file, String textureFile) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(CombatAnim.modId(), "textures/entity/" + textureFile);
        JsonObject json = CombatAnimations.readJson("/assets/" + CombatAnim.modId() + "/combat_animations/" + file).getAsJsonObject();
        List<Segment> segments = new ArrayList<>();
        for (JsonElement s : json.getAsJsonArray("segments")) {
            JsonObject so = s.getAsJsonObject();
            List<Quad> quads = new ArrayList<>();
            for (JsonElement q : so.getAsJsonArray("quads")) {
                JsonObject qo = q.getAsJsonObject();
                quads.add(new Quad(matrix(qo.getAsJsonArray("v")), matrix(qo.getAsJsonArray("uv")), vector(qo.getAsJsonArray("n"))));
            }
            segments.add(new Segment(Part.valueOf(so.get("part").getAsString().toUpperCase()), texture, quads));
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
            List<Window> windows = WINDOWS.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            for (JsonElement w : e.getValue().getAsJsonArray()) {
                JsonArray wa = w.getAsJsonArray();
                windows.add(new Window(segments.get(wa.get(0).getAsInt()), wa.get(1).getAsFloat(), wa.get(2).getAsFloat()));
            }
        }
    }

    private static float[][] matrix(JsonArray a) {
        float[][] m = new float[a.size()][];
        for (int i = 0; i < m.length; i++) m[i] = vector(a.get(i).getAsJsonArray());
        return m;
    }

    private static float[] vector(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    /** Tensura: Fragments - how far above the floor the ground shockwave lies (blocks), so it never sinks into it. */
    private static final double FLOOR_LIFT = 0.1;

    /**
     * Tensura: Fragments - puts the ground shockwave (drawn at the model's feet) on the floor under the player, a hair
     * above it: not sunk in by the crouch (sneaking lowers the model), not hanging in the air when the move is thrown
     * mid-air, and not hidden under the blocks around a lower one (a path, a slab).
     */
    private static void onFloor(PoseStack poseStack, AbstractClientPlayer player, float partialTick) {
        net.minecraft.world.phys.Vec3 at = player.getPosition(partialTick);
        net.minecraft.world.phys.HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                at.add(0, 0.5, 0), at.subtract(0, 4, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        double floor = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at.y : hit.getLocation().y;
        double offset = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player)
                .getRenderOffset(player, partialTick).y;
        // Model space: y runs down, in blocks scaled by the player renderer (0.9375) and the player's own scale.
        float scale = 0.9375F * player.getScale();
        double drawnAt = at.y + offset + (1.501 - 23.9 / 16) * scale;
        poseStack.translate(0, -(floor + FLOOR_LIFT - drawnAt) / scale, 0);
    }

    /** Draws the trails on the player model after it has been posed. */
    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) return;
            Time time = CombatAnimator.trailTime(player, ageInTicks);
            if (time == null) return;
            List<Window> windows = WINDOWS.get(time.animation());
            if (windows == null) return;

            PlayerModel<AbstractClientPlayer> model = getParentModel();
            for (Window w : windows) {
                if (time.seconds() < w.on() || time.seconds() >= w.off()) continue;
                VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentCull(w.segment().texture()));
                ModelPart part = switch (w.segment().part()) {
                    case BODY -> model.body;
                    case RIGHT_ARM -> model.rightArm;
                    case LEFT_ARM -> model.leftArm;
                    case GROUND -> null;
                };
                poseStack.pushPose();
                if (part != null) part.translateAndRotate(poseStack);
                else onFloor(poseStack, player, partialTick);
                PoseStack.Pose pose = poseStack.last();
                for (Quad q : w.segment().quads()) {
                    for (int i = 0; i < 4; i++) {
                        // Model pixels to blocks, like ModelPart cubes.
                        buffer.addVertex(pose, q.v()[i][0] / 16f, q.v()[i][1] / 16f, q.v()[i][2] / 16f)
                                .setColor(1f, 1f, 1f, 1f)
                                .setUv(q.uv()[i][0], q.uv()[i][1])
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(LightTexture.FULL_BRIGHT)
                                .setNormal(pose, q.n()[0], q.n()[1], q.n()[2]);
                    }
                }
                poseStack.popPose();
            }
        }
    }
}
