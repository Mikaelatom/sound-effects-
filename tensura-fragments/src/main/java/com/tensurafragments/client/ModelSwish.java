package com.tensurafragments.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tensurafragments.TensuraFragments;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The punch swish as the combat animation pack builds it: flat trail shapes on the player model (the {@code swish_*}
 * bones of its player.geo.json), hung off the body or an arm, and flicked on frame by frame by each punch animation's
 * scale keys (player_combat.animation.json). A hook's swish lies flat at shoulder height and sweeps across the front;
 * a jab's runs along the punching arm; the uppercut's rises in front.
 */
public final class ModelSwish {
    /** The swish: 14 frames of 48x48, top to bottom (the arc growing, sweeping across, and fading). */
    public static final ResourceLocation TEXTURE = TensuraFragments.id("textures/entity/swish_strip.png");
    private static final int FRAMES = 14;

    /** A face of a swish cube: which way it faces and its texture area (pixels; a negative size is mirrored). */
    private record Face(String side, float u, float v, float du, float dv) {
    }

    private record Cube(float[] origin, float[] size, List<Face> faces) {
    }

    private record Bone(String name, @Nullable String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
    }

    /** A scale track: time (seconds) to the scale before and after that key. */
    private record Track(TreeMap<Float, float[]> keys, float constant) {
        /** When the sheet is first shown and when it's last hidden (seconds), or null if never. */
        float[] shown() {
            if (keys == null) {
                return null;
            }
            Float on = null;
            Float off = null;
            for (Map.Entry<Float, float[]> key : keys.entrySet()) {
                if (key.getValue()[1] > 0 && on == null) {
                    on = key.getKey();
                }
                if (on != null && key.getValue()[1] <= 0) {
                    off = key.getKey();
                }
            }
            return on == null ? null : new float[] {on, off == null ? on + 0.3F : off};
        }

        float at(float time) {
            if (keys == null) {
                return constant;
            }
            Map.Entry<Float, float[]> key = keys.floorEntry(time);
            return key == null ? keys.firstEntry().getValue()[0] : key.getValue()[1];
        }
    }

    private static Map<String, Bone> bones;
    /** For each animation (by its short name, like punch_hook_right), each swish bone's scale track. */
    private static Map<String, Map<String, Track>> tracks;

    private ModelSwish() {
    }

    private static JsonObject read(String file) throws Exception {
        try (InputStream in = ModelSwish.class.getResourceAsStream("/assets/" + TensuraFragments.MODID + "/swish/" + file)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static float[] floats(JsonElement e) {
        if (e == null) {
            return new float[3];
        }
        if (!e.isJsonArray()) {
            float f = e.getAsFloat();
            return new float[] {f, f, f};
        }
        JsonArray a = e.getAsJsonArray();
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    private static boolean loaded() {
        if (bones != null) {
            return !bones.isEmpty();
        }
        bones = new HashMap<>();
        tracks = new HashMap<>();
        try {
            JsonObject geo = read("player.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            for (JsonElement e : geo.getAsJsonArray("bones")) {
                JsonObject b = e.getAsJsonObject();
                List<Cube> cubes = new ArrayList<>();
                if (b.has("cubes")) {
                    for (JsonElement ce : b.getAsJsonArray("cubes")) {
                        JsonObject c = ce.getAsJsonObject();
                        List<Face> faces = new ArrayList<>();
                        if (c.has("uv") && c.get("uv").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> f : c.getAsJsonObject("uv").entrySet()) {
                                JsonObject fo = f.getValue().getAsJsonObject();
                                float[] uv = floats(fo.get("uv"));
                                float[] us = floats(fo.get("uv_size"));
                                faces.add(new Face(f.getKey(), uv[0], uv[1], us[0], us[1]));
                            }
                        }
                        cubes.add(new Cube(floats(c.get("origin")), floats(c.get("size")), faces));
                    }
                }
                String name = b.get("name").getAsString();
                bones.put(name, new Bone(name, b.has("parent") ? b.get("parent").getAsString() : null,
                        floats(b.get("pivot")), b.has("rotation") ? floats(b.get("rotation")) : new float[3], cubes));
            }
            JsonObject animations = read("player_combat.animation.json").getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> a : animations.entrySet()) {
                String name = a.getKey().substring(a.getKey().lastIndexOf('.') + 1);
                JsonObject animBones = a.getValue().getAsJsonObject().getAsJsonObject("bones");
                Map<String, Track> perBone = new HashMap<>();
                if (animBones != null) {
                    for (Map.Entry<String, JsonElement> b : animBones.entrySet()) {
                        if (!b.getKey().startsWith("swish") || !b.getValue().getAsJsonObject().has("scale")) {
                            continue;
                        }
                        JsonElement scale = b.getValue().getAsJsonObject().get("scale");
                        if (scale.isJsonPrimitive()) {
                            perBone.put(b.getKey(), new Track(null, scale.getAsFloat()));
                            continue;
                        }
                        if (scale.isJsonArray()) {
                            perBone.put(b.getKey(), new Track(null, floats(scale)[0]));
                            continue;
                        }
                        TreeMap<Float, float[]> keys = new TreeMap<>();
                        for (Map.Entry<String, JsonElement> k : scale.getAsJsonObject().entrySet()) {
                            JsonElement v = k.getValue();
                            float pre;
                            float post;
                            if (v.isJsonObject()) {
                                JsonObject o = v.getAsJsonObject();
                                post = floats(o.has("post") ? o.get("post") : o.get("pre"))[0];
                                pre = floats(o.has("pre") ? o.get("pre") : o.get("post"))[0];
                            } else {
                                pre = post = floats(v)[0];
                            }
                            keys.put(Float.parseFloat(k.getKey()), new float[] {pre, post});
                        }
                        perBone.put(b.getKey(), new Track(keys, 0));
                    }
                }
                tracks.put(name, perBone);
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Couldn't load the punch swish model", e);
            bones.clear();
        }
        return !bones.isEmpty();
    }

    /** Whether the animation shows any swish at this moment. */
    public static boolean showing(String animation, float seconds) {
        if (!loaded()) {
            return false;
        }
        Map<String, Track> perBone = tracks.get(animation);
        if (perBone == null) {
            return false;
        }
        for (Track track : perBone.values()) {
            if (track.at(seconds) > 0) {
                return true;
            }
        }
        return false;
    }

    /** The model part a swish bone hangs off, and that part's resting pivot (in the geo file's coordinates). */
    private static ModelPart anchor(HumanoidModel<?> model, String bone) {
        return switch (bone) {
            case "rightArm" -> model.rightArm;
            case "leftArm" -> model.leftArm;
            default -> model.body;
        };
    }

    private static float[] anchorPivot(String bone) {
        return switch (bone) {
            case "rightArm" -> new float[] {-5, 22, 0};
            case "leftArm" -> new float[] {5, 22, 0};
            // The game's body pivots at the neck (the geo's body pivots at the waist).
            default -> new float[] {0, 24, 0};
        };
    }

    /**
     * Draws the swish showing at this moment of the animation onto a player model already posed for it (in the
     * model's own space, as a render layer gets it).
     */
    public static void render(HumanoidModel<?> model, String animation, float seconds, PoseStack stack,
                              VertexConsumer out) {
        if (!loaded()) {
            return;
        }
        Map<String, Track> perBone = tracks.get(animation);
        if (perBone == null) {
            return;
        }
        // Each swish (a group like swish_hook_r) plays the swish strip's frames over the time its sheets are shown.
        Map<String, float[]> windows = new HashMap<>();
        for (Map.Entry<String, Track> entry : perBone.entrySet()) {
            Bone bone = bones.get(entry.getKey());
            float[] shown = entry.getValue().shown();
            if (bone == null || bone.parent() == null || shown == null) {
                continue;
            }
            windows.merge(bone.parent(), shown, (x, y) -> new float[] {Math.min(x[0], y[0]), Math.max(x[1], y[1])});
        }
        for (Map.Entry<String, float[]> window : windows.entrySet()) {
            float[] w = window.getValue();
            if (seconds < w[0] || seconds >= w[1]) {
                continue;
            }
            int frame = Mth.clamp((int) ((seconds - w[0]) / (w[1] - w[0]) * FRAMES), 0, FRAMES - 1);
            Bone group = bones.get(window.getKey());
            if (group == null) {
                continue;
            }
            // Every sheet of it that's on right now (a hook's are stacked on one spot, so they build it up as it goes;
            // a jab's are pieces along the arm).
            for (Bone bone : bones.values()) {
                Track track = perBone.get(bone.name());
                if (!window.getKey().equals(bone.parent()) || bone.cubes().isEmpty() || track == null
                        || track.at(seconds) <= 0) {
                    continue;
                }
                stack.pushPose();
                float[] from = place(model, bone, stack);
                int flip = window.getKey().startsWith("swish_upper") ? uppercutFlip : HOOK_FLIP;
                int shownFrame = (flip & REVERSE) != 0 ? FRAMES - 1 - frame : frame;
                for (Cube cube : bone.cubes()) {
                    drawCube(stack.last(), out, cube, from, shownFrame, flip);
                }
                stack.popPose();
            }
        }
    }

    /** Moves the pose to a swish bone (through its parents, from the model part it hangs off); returns its pivot. */
    private static float[] place(HumanoidModel<?> model, Bone bone, PoseStack stack) {
        List<Bone> chain = new ArrayList<>();
        Bone b = bone;
        while (b != null && b.name().startsWith("swish")) {
            chain.add(0, b);
            b = b.parent() == null ? null : bones.get(b.parent());
        }
        String root = b == null ? "body" : b.name();
        anchor(model, root).translateAndRotate(stack);
        float[] from = anchorPivot(root);
        for (Bone link : chain) {
            float[] p = link.pivot();
            // Geo coordinates (y up from the feet) to the model's (y down from the neck).
            stack.translate((p[0] - from[0]) / 16F, -(p[1] - from[1]) / 16F, (p[2] - from[2]) / 16F);
            float[] r = link.rotation();
            if (r[2] != 0) {
                stack.mulPose(Axis.ZP.rotationDegrees(r[2]));
            }
            if (r[1] != 0) {
                stack.mulPose(Axis.YP.rotationDegrees(r[1]));
            }
            if (r[0] != 0) {
                stack.mulPose(Axis.XP.rotationDegrees(r[0]));
            }
            from = p;
        }
        return from;
    }

    /** A flat swish cube: its south face (for a sheet in x and y) or west face (in z and y), seen from both sides. */
    /** How a swish's frame is laid on its sheet: mirrored side to side, and/or upside down. */
    static final int FLIP_U = 1;
    static final int FLIP_V = 2;
    /** The frames played last to first. */
    static final int REVERSE = 4;
    /** The hooks' arcs sweep across the body the way the fist goes with the frame mirrored. */
    private static final int HOOK_FLIP = FLIP_U;
    /**
     * The uppercut's sheet stands upright beside the rising fist with the frame laid as the pack has it, but the
     * strip's arc grows downward on it, so it plays last to first to rise with the fist.
     */
    public static int uppercutFlip = REVERSE;

    private static void drawCube(PoseStack.Pose pose, VertexConsumer out, Cube cube, float[] pivot, int frame,
                                 int flip) {
        float x0 = cube.origin()[0] - pivot[0];
        float x1 = x0 + cube.size()[0];
        // y up in the geo; the model's y runs down.
        float yTop = -((cube.origin()[1] + cube.size()[1]) - pivot[1]);
        float yBottom = -(cube.origin()[1] - pivot[1]);
        float z0 = cube.origin()[2] - pivot[2];
        float z1 = z0 + cube.size()[2];
        // The whole frame of the swish strip on the sheet, laid so the arc sweeps the way the fist travels.
        float u0 = (flip & FLIP_U) != 0 ? 1 : 0;
        float u1 = 1 - u0;
        float v0 = frame / (float) FRAMES;
        float v1 = (frame + 1) / (float) FRAMES;
        if ((flip & FLIP_V) != 0) {
            float v = v0;
            v0 = v1;
            v1 = v;
        }
        for (Face face : cube.faces()) {
            if (face.side().equals("south") && cube.size()[2] == 0) {
                quad(pose, out, x0, yTop, z0, x1, yTop, z0, x1, yBottom, z0, x0, yBottom, z0, u0, v0, u1, v1, 0, 0, 1);
            } else if (face.side().equals("west") && cube.size()[0] == 0) {
                quad(pose, out, x0, yTop, z0, x0, yTop, z1, x0, yBottom, z1, x0, yBottom, z0, u0, v0, u1, v1, -1, 0, 0);
            }
        }
    }

    /** Corners: top-left, top-right, bottom-right, bottom-left (as the texture area is laid out). */
    private static void quad(PoseStack.Pose pose, VertexConsumer out, float ax, float ay, float az, float bx, float by,
                             float bz, float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0,
                             float u1, float v1, float nx, float ny, float nz) {
        vertex(pose, out, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(pose, out, dx, dy, dz, u0, v1, nx, ny, nz);
        vertex(pose, out, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(pose, out, bx, by, bz, u1, v0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(pose, x / 16F, y / 16F, z / 16F).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                // Lit straight on from either side (the shading of a sheet lying flat would turn it grey).
                .setNormal(0, 1, 0);
    }
}
