package com.tensurafragments.combatanim.client;

import com.tensurafragments.combatanim.CombatAnim;
import com.tensurafragments.combatanim.CombatAnimations;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The combat effects modelled in player_moves.bbmodel (hit sparks, guard, dash trails, ki beam, ...): flat
 * textured planes that pop on and off frame by frame, exactly like in Blockbench.
 * Data combat_animations/combat_fx.json (exported with tools/SwishExport.java ... fx), texture
 * textures/entity/combat_fx.png. Don't edit the JSON by hand.
 *
 * <p>Two ways they play:
 * <ul>
 *   <li>With an animation: playing e.g. {@code ki_blast} shows its effects. Parts on the body, arms or legs are
 *       drawn on the posed player model ({@link Layer}, third person). Effects on the ground ("ground" follows the
 *       player, "start" stays where the animation started, "floor" is that spot snapped down to the floor) play on
 *       their own: they finish even if the player starts another move, and show in first person too.</li>
 *   <li>Triggered on any entity with {@link CombatAnim#effect}: the fx_* clips (fx_hit_spark, fx_finisher,
 *       fx_knockdown, fx_stun_mark), scaled to the entity's height and facing the attacker.</li>
 * </ul>
 */
public final class CombatFx {
    private enum Part {
        BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, GROUND, START, FLOOR;

        boolean posed() {
            return ordinal() <= LEFT_LEG.ordinal();
        }
    }

    private record Quad(float[][] v, float[][] uv, float[] n) {}

    private record Segment(Part part, List<Quad> quads) {}

    /** A segment is shown from {@code on} (inclusive) to {@code off} (exclusive), in seconds. */
    private record Window(Segment segment, float on, float off) {}

    private record Clip(float length, boolean loop) {}

    /** Rest pivots of the vanilla parts in model pixels, for posed parts drawn without a model. */
    private static final float[][] REST = {{0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    private static final float PLAYER_SCALE = 0.9375f;   // PlayerRenderer.scale
    private static final double MAX_DISTANCE_SQR = 96 * 96;

    private static final Map<String, List<Window>> WINDOWS = new HashMap<>();
    private static final Map<String, Clip> CLIPS = new LinkedHashMap<>();
    private static ResourceLocation texture;

    /** An effect playing on its own, not drawn as part of the posed player model. */
    private static final class Instance {
        int entityId;
        String name;
        List<Window> windows;
        float startTick;          // level game time
        float length;             // seconds; loops wrap at this
        boolean loop;
        float endSeconds;         // remove after this
        float animStart = Float.NaN;  // set for loop animations: lives while that animation keeps playing
        float yaw = Float.NaN;        // NaN = follow the entity's body
        float scale = PLAYER_SCALE;
        Vec3 anchor;              // where the animation started, for START / FLOOR parts
        float anchorYaw;
        Vec3 lastPos;             // keeps drawing where the entity was if it disappears (e.g. killed by the finisher)
        float lastYaw;
    }

    private static final List<Instance> ACTIVE = new ArrayList<>();

    private CombatFx() {}

    public static void load() {
        String path = "/assets/" + CombatAnim.modId() + "/combat_animations/combat_fx.json";
        if (CombatFx.class.getResource(path) == null) return;   // effects are optional: delete the file to drop them
        texture = ResourceLocation.fromNamespaceAndPath(CombatAnim.modId(), "textures/entity/combat_fx.png");
        JsonObject json = CombatAnimations.readJson(path).getAsJsonObject();
        List<Segment> segments = new ArrayList<>();
        for (JsonElement s : json.getAsJsonArray("segments")) {
            JsonObject so = s.getAsJsonObject();
            List<Quad> quads = new ArrayList<>();
            for (JsonElement q : so.getAsJsonArray("quads")) {
                JsonObject qo = q.getAsJsonObject();
                quads.add(new Quad(matrix(qo.getAsJsonArray("v")), matrix(qo.getAsJsonArray("uv")), vector(qo.getAsJsonArray("n"))));
            }
            segments.add(new Segment(Part.valueOf(so.get("part").getAsString().toUpperCase()), quads));
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
            List<Window> windows = WINDOWS.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            for (JsonElement w : e.getValue().getAsJsonArray()) {
                JsonArray wa = w.getAsJsonArray();
                windows.add(new Window(segments.get(wa.get(0).getAsInt()), wa.get(1).getAsFloat(), wa.get(2).getAsFloat()));
            }
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("clips").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            CLIPS.put(e.getKey(), new Clip(c.get("length").getAsFloat(), c.get("loop").getAsBoolean()));
        }
    }

    /** The triggerable clips (fx_hit_spark, fx_finisher, fx_knockdown, fx_stun_mark). */
    public static Set<String> clipNames() {
        return Collections.unmodifiableSet(CLIPS.keySet());
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

    private static float maxOff(List<Window> windows) {
        float end = 0f;
        for (Window w : windows) end = Math.max(end, w.off());
        return end;
    }

    /** Called when a player starts an animation: starts the parts of its effects that aren't on the posed model. */
    static void onPlay(Player player, String name, CombatAnimations.Anim anim, float startTick) {
        List<Window> all = WINDOWS.get(name);
        if (all == null) return;
        List<Window> windows = new ArrayList<>();
        for (Window w : all) if (!w.segment().part().posed()) windows.add(w);
        if (windows.isEmpty()) return;
        Instance in = new Instance();
        in.entityId = player.getId();
        in.name = name;
        in.windows = windows;
        in.startTick = player.level().getGameTime();
        in.loop = anim.mode() == CombatAnimations.Mode.LOOP;
        in.length = anim.lengthSeconds();
        in.endSeconds = in.loop ? Float.MAX_VALUE : maxOff(windows);
        if (in.loop) in.animStart = startTick;
        anchor(in, player);
        ACTIVE.add(in);
    }

    /**
     * Play a clip (or any animation's effects) on an entity. {@code yaw} is the direction the effect faces
     * (NaN = the entity's body). {@code durationTicks}: how long a looping clip runs (0 = one loop); -1 stops it.
     */
    public static void trigger(Entity entity, String name, float yaw, int durationTicks) {
        if (durationTicks < 0) {
            ACTIVE.removeIf(in -> in.entityId == entity.getId() && in.name.equals(name) && Float.isNaN(in.animStart));
            return;
        }
        List<Window> windows = WINDOWS.get(name);
        if (windows == null) return;
        Clip clip = CLIPS.getOrDefault(name, new Clip(maxOff(windows), false));
        Instance in = new Instance();
        in.entityId = entity.getId();
        in.name = name;
        in.windows = windows;
        in.startTick = entity.level().getGameTime();
        in.loop = clip.loop();
        in.length = clip.length();
        in.endSeconds = clip.loop() && durationTicks > 0 ? durationTicks / 20f : clip.length();
        in.yaw = yaw;
        if (!(entity instanceof Player)) {
            in.scale = PLAYER_SCALE * Mth.clamp(entity.getBbHeight() / 1.8f, 0.5f, 3f);
        }
        anchor(in, entity);
        ACTIVE.add(in);
    }

    private static void anchor(Instance in, Entity entity) {
        in.anchor = entity.position();
        in.anchorYaw = bodyYaw(entity, 1f);
        in.lastPos = in.anchor;
        in.lastYaw = in.anchorYaw;
        boolean floor = false;
        for (Window w : in.windows) floor |= w.segment().part() == Part.FLOOR;
        if (floor) {
            Vec3 down = in.anchor.subtract(0, 16, 0);
            HitResult hit = entity.level().clip(new ClipContext(in.anchor.add(0, 0.1, 0), down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) in.anchor = hit.getLocation();
        }
    }

    private static float bodyYaw(Entity entity, float partialTick) {
        if (entity instanceof LivingEntity living) return Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        return entity.getViewYRot(partialTick);
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        float now = level.getGameTime();
        Iterator<Instance> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Instance in = it.next();
            boolean done = (now - in.startTick) / 20f > in.endSeconds;
            if (!Float.isNaN(in.animStart)) {
                done = !(level.getEntity(in.entityId) instanceof Player p) || !CombatAnimator.isPlaying(p, in.name, in.animStart);
            }
            if (done) it.remove();
        }
    }

    /** Draws the effects playing on their own, in world space. Fired after entities, before they are flushed. */
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partialTick;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentCull(texture));

        for (Instance in : ACTIVE) {
            Entity entity = level.getEntity(in.entityId);
            if (entity != null) {
                if (entity.isInvisible()) continue;
                in.lastPos = entity.getPosition(partialTick);
                in.lastYaw = bodyYaw(entity, partialTick);
            }
            if (in.lastPos.distanceToSqr(cam) > MAX_DISTANCE_SQR) continue;
            float seconds = (now - in.startTick) / 20f;
            if (seconds > in.endSeconds) continue;
            if (in.loop) seconds %= in.length;
            for (Window w : in.windows) {
                if (seconds < w.on() || seconds >= w.off()) continue;
                Part part = w.segment().part();
                boolean anchored = part == Part.START || part == Part.FLOOR;
                Vec3 base = anchored ? in.anchor : in.lastPos;
                // Tensura: Fragments - floor effects lie a hair above the floor they're snapped to, not in it.
                if (part == Part.FLOOR) base = base.add(0, 0.1, 0);
                float yaw = anchored ? in.anchorYaw : Float.isNaN(in.yaw) ? in.lastYaw : in.yaw;
                poseStack.pushPose();
                poseStack.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);
                // The same transform LivingEntityRenderer gives the player model, so model space matches the layer.
                poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));
                poseStack.scale(-in.scale, -in.scale, in.scale);
                poseStack.translate(0f, -1.501f, 0f);
                if (part.posed()) {
                    float[] rest = REST[part.ordinal()];
                    poseStack.translate(rest[0] / 16f, rest[1] / 16f, rest[2] / 16f);
                }
                draw(poseStack.last(), buffer, w.segment());
                poseStack.popPose();
            }
        }
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Segment segment) {
        for (Quad q : segment.quads()) {
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
    }

    /** Draws the effects that hang off the body, arms and legs on the posed player model (third person). */
    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (texture == null || player.isInvisible()) return;
            SwishTrails.Time time = CombatAnimator.trailTime(player, ageInTicks);
            if (time == null) return;
            List<Window> windows = WINDOWS.get(time.animation());
            if (windows == null) return;

            PlayerModel<AbstractClientPlayer> model = getParentModel();
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentCull(texture));
            for (Window w : windows) {
                if (!w.segment().part().posed() || time.seconds() < w.on() || time.seconds() >= w.off()) continue;
                ModelPart part = switch (w.segment().part()) {
                    case RIGHT_ARM -> model.rightArm;
                    case LEFT_ARM -> model.leftArm;
                    case RIGHT_LEG -> model.rightLeg;
                    case LEFT_LEG -> model.leftLeg;
                    default -> model.body;
                };
                poseStack.pushPose();
                part.translateAndRotate(poseStack);
                draw(poseStack.last(), buffer, w.segment());
                poseStack.popPose();
            }
        }
    }
}
