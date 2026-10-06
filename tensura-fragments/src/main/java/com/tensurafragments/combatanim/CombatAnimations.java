package com.tensurafragments.combatanim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The animation data, exported from Blockbench and baked at 40 fps.
 * Each frame stores, per player model part, the pivot offset from the vanilla rest pivot
 * (model pixels, Minecraft model space) and the part's rotation as a quaternion.
 *
 * <p>Safe to use on both sides, e.g. to read an animation's length for a server-side cooldown.
 * Loads from assets/&lt;modId&gt;/combat_animations/ the first time it's used.
 */
public final class CombatAnimations {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    private static final String[] PART_KEYS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private static Map<String, Anim> anims;

    /** How an animation behaves when it reaches its last frame. */
    public enum Mode {
        /** Play once, then blend back to the normal pose. */
        ONCE,
        /** Play once, then stay on the last frame until stopped or replaced. */
        HOLD,
        /** Repeat until stopped or replaced. */
        LOOP
    }

    private CombatAnimations() {}

    /** The animation with this name, or null if there isn't one. */
    public static Anim get(String name) {
        return all().get(name);
    }

    /** Every animation name, in index.json order. */
    public static Set<String> names() {
        return Collections.unmodifiableSet(all().keySet());
    }

    private static synchronized Map<String, Anim> all() {
        if (anims == null) {
            String root = "/assets/" + CombatAnim.modId() + "/combat_animations/";
            Map<String, Anim> loaded = new LinkedHashMap<>();
            for (JsonElement name : readJson(root + "index.json").getAsJsonArray()) {
                String n = name.getAsString();
                loaded.put(n, Anim.fromJson(readJson(root + n + ".json").getAsJsonObject()));
            }
            anims = loaded;
        }
        return anims;
    }

    /** Reads a JSON file from the mod jar, e.g. "/assets/yourmod/combat_animations/index.json". */
    public static JsonElement readJson(String path) {
        try (InputStream in = CombatAnimations.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Combat animations: could not load " + path, e);
        }
    }

    public record Anim(float length, float fps, float strike, Mode mode, float[][][] frames) {
        static Anim fromJson(JsonObject json) {
            JsonObject parts = json.getAsJsonObject("parts");
            float[][][] frames = new float[PART_KEYS.length][][];
            for (int p = 0; p < PART_KEYS.length; p++) {
                JsonArray list = parts.getAsJsonArray(PART_KEYS[p]);
                frames[p] = new float[list.size()][7];
                for (int f = 0; f < list.size(); f++) {
                    JsonArray v = list.get(f).getAsJsonArray();
                    for (int k = 0; k < 7; k++) frames[p][f][k] = v.get(k).getAsFloat();
                }
            }
            float strike = json.has("strike") && !json.get("strike").isJsonNull() ? json.get("strike").getAsFloat() : -1f;
            Mode mode = json.has("mode") ? Mode.valueOf(json.get("mode").getAsString().toUpperCase()) : Mode.ONCE;
            return new Anim(json.get("length").getAsFloat(), json.get("fps").getAsFloat(), strike, mode, frames);
        }

        /** Length in seconds. */
        public float lengthSeconds() {
            return length;
        }

        public float lengthTicks() {
            return length * 20f;
        }

        /** When the punch lands, in seconds, or a negative number if the animation has no strike (only the 5 punch_* ones do). */
        public float strikeSeconds() {
            return strike;
        }

        public void sample(float seconds, int part, Vector3f pos, Quaternionf rot) {
            float[][] f = frames[part];
            float x = Mth.clamp(seconds * fps, 0f, f.length - 1);
            int i = (int) x;
            int j = Math.min(i + 1, f.length - 1);
            float a = x - i;
            float[] A = f[i], B = f[j];
            pos.set(Mth.lerp(a, A[0], B[0]), Mth.lerp(a, A[1], B[1]), Mth.lerp(a, A[2], B[2]));
            rot.set(A[3], A[4], A[5], A[6]).slerp(new Quaternionf(B[3], B[4], B[5], B[6]), a);
        }
    }
}
