package com.tensurafragments.rune;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;

/**
 * The runes of Rune Magic. Each is drawn on a 5 by 5 grid of dots: its shape is a set of strokes, each a straight line
 * across, down, or diagonally between dots. What you draw has to match a rune's lines exactly (the order and
 * direction you draw them in don't matter).
 */
public enum Rune {
    FIRE("fire", 0xFF7A2E, new int[][] {{0, 4, 2, 2, 4, 4}, {2, 2, 2, 0}}),
    FROST("frost", 0x9EE7FF, new int[][] {{2, 0, 2, 4}, {0, 2, 4, 2}, {1, 1, 3, 3}, {3, 1, 1, 3}}),
    THUNDER("thunder", 0xFFE45C, new int[][] {{3, 0, 1, 2, 3, 2, 1, 4}}),
    LIFE("life", 0x7CFF8A, new int[][] {{2, 4, 2, 2, 0, 0}, {2, 2, 4, 0}}),
    GUARD("guard", 0xC9D3FF, new int[][] {{0, 0, 4, 0, 4, 2, 2, 4, 0, 2, 0, 0}}),
    WIND("wind", 0xE4FFF6, new int[][] {{0, 2, 4, 2}, {2, 0, 4, 2, 2, 4}}),
    BIND("bind", 0xC27CFF, new int[][] {{0, 0, 4, 4, 4, 0, 0, 4, 0, 0}}),
    SIGHT("sight", 0xFFFFFF, new int[][] {{2, 0, 4, 2, 2, 4, 0, 2, 2, 0}, {2, 1, 2, 3}});

    /** Dots per side. */
    public static final int GRID = 5;

    private final String id;
    private final int colour;
    private final int[][] strokes;
    private final Set<Integer> segments;

    Rune(String id, int colour, int[][] strokes) {
        this.id = id;
        this.colour = colour;
        this.strokes = strokes;
        this.segments = new TreeSet<>();
        for (int[] stroke : strokes) {
            for (int i = 0; i + 3 < stroke.length; i += 2) {
                line(stroke[i], stroke[i + 1], stroke[i + 2], stroke[i + 3], segments);
            }
        }
    }

    public String id() {
        return id;
    }

    public int colour() {
        return colour;
    }

    /** The strokes as flat lists of x, y dot coordinates (for drawing the rune in the codex). */
    public int[][] strokes() {
        return strokes;
    }

    /** The unit segments that make up this rune. */
    public Set<Integer> segments() {
        return segments;
    }

    public String translationKey() {
        return "tensurafragments.rune." + id;
    }

    @Nullable
    public static Rune byId(String id) {
        for (Rune rune : values()) {
            if (rune.id.equals(id)) {
                return rune;
            }
        }
        return null;
    }

    /** The rune exactly matching these drawn segments, or null. */
    @Nullable
    public static Rune match(Collection<Integer> drawn) {
        Set<Integer> set = new TreeSet<>(drawn);
        for (Rune rune : values()) {
            if (rune.segments.equals(set)) {
                return rune;
            }
        }
        return null;
    }

    // ---- Segments: a line between two neighbouring dots, as one number ----

    public static int dot(int x, int y) {
        return y * GRID + x;
    }

    public static boolean onGrid(int x, int y) {
        return x >= 0 && y >= 0 && x < GRID && y < GRID;
    }

    /** The segment between two neighbouring dots (either order gives the same number). */
    public static int segment(int a, int b) {
        return Math.min(a, b) * GRID * GRID + Math.max(a, b);
    }

    /** The two dots a segment joins. */
    public static int[] ends(int segment) {
        int a = segment / (GRID * GRID);
        int b = segment % (GRID * GRID);
        return new int[] {a % GRID, a / GRID, b % GRID, b / GRID};
    }

    /**
     * Adds the unit segments of a straight line from one dot to another, if it runs across, down or diagonally.
     * Returns whether it did (a line at any other angle isn't a rune line).
     */
    public static boolean line(int x0, int y0, int x1, int y1, Collection<Integer> out) {
        int dx = Integer.signum(x1 - x0);
        int dy = Integer.signum(y1 - y0);
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        boolean straight = x0 == x1 || y0 == y1 || Math.abs(x1 - x0) == Math.abs(y1 - y0);
        if (!straight || steps == 0 || !onGrid(x0, y0) || !onGrid(x1, y1)) {
            return false;
        }
        int x = x0;
        int y = y0;
        for (int i = 0; i < steps; i++) {
            out.add(segment(dot(x, y), dot(x + dx, y + dy)));
            x += dx;
            y += dy;
        }
        return true;
    }

    /** Every segment of every rune (for tests and checks). */
    public static List<Set<Integer>> all() {
        List<Set<Integer>> list = new ArrayList<>();
        for (Rune rune : values()) {
            list.add(rune.segments);
        }
        return list;
    }
}
