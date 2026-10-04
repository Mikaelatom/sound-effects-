package com.tensurafragments.client;

import com.tensurafragments.network.DrawRunePayload;
import com.tensurafragments.rune.Rune;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Rune Magic's screens: the drawing sheet and the Rune Codex. */
public final class RuneScreens {
    private static final int PAPER = 0xFFEDE3C8;
    private static final int PAPER_EDGE = 0xFF8C7650;
    private static final int INK = 0xFF3B2416;
    private static final int DOT = 0xFF6E5A3C;

    private RuneScreens() {
    }

    public static void openCanvas() {
        Minecraft.getInstance().setScreen(new Canvas());
    }

    public static void openCodex() {
        Minecraft.getInstance().setScreen(new Codex(Minecraft.getInstance().screen instanceof Canvas canvas ? canvas : null));
    }

    /** A thick straight line, drawn as small squares along it. */
    static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1, int thickness, int colour) {
        float length = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, Math.round(length));
        int half = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            graphics.fill(x - half, y - half, x - half + thickness, y - half + thickness, colour);
        }
    }

    /** The dot grid and some segments, at a spot and spacing. */
    static void grid(GuiGraphics graphics, int left, int top, int spacing, Iterable<Integer> segments, int ink, int thickness) {
        for (int segment : segments) {
            int[] e = Rune.ends(segment);
            line(graphics, left + e[0] * spacing, top + e[1] * spacing, left + e[2] * spacing, top + e[3] * spacing, thickness,
                    ink);
        }
        int dot = Math.max(2, spacing / 8);
        for (int y = 0; y < Rune.GRID; y++) {
            for (int x = 0; x < Rune.GRID; x++) {
                graphics.fill(left + x * spacing - dot, top + y * spacing - dot, left + x * spacing + dot,
                        top + y * spacing + dot, DOT);
            }
        }
    }

    // ---- Drawing sheet ----

    /** Connect the dots: press on a dot and drag through others; each straight run between dots is a line. */
    public static final class Canvas extends Screen {
        private static final int SPACING = 40;
        private final List<Set<Integer>> strokes = new ArrayList<>();
        private int lastDot = -1;
        private double mouseX;
        private double mouseY;
        private int left;
        private int top;

        Canvas() {
            super(Component.translatable("tensurafragments.rune.canvas"));
        }

        @Override
        protected void init() {
            left = width / 2 - SPACING * 2;
            top = height / 2 - SPACING * 2 - 6;
            int y = top + SPACING * 4 + 22;
            addRenderableWidget(Button.builder(Component.translatable("tensurafragments.rune.undo"), b -> {
                if (!strokes.isEmpty()) {
                    strokes.remove(strokes.size() - 1);
                }
            }).bounds(width / 2 - 154, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("tensurafragments.rune.clear"), b -> strokes.clear())
                    .bounds(width / 2 - 78, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("tensurafragments.rune.codex"), b -> openCodex())
                    .bounds(width / 2 - 2, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("tensurafragments.rune.inscribe"), b -> inscribe())
                    .bounds(width / 2 + 74, y, 80, 20).build());
        }

        private Set<Integer> drawn() {
            Set<Integer> all = new LinkedHashSet<>();
            strokes.forEach(all::addAll);
            return all;
        }

        private void inscribe() {
            Set<Integer> drawn = drawn();
            if (!drawn.isEmpty()) {
                PacketDistributor.sendToServer(new DrawRunePayload(new ArrayList<>(drawn)));
            }
            onClose();
        }

        /** The dot under the mouse, or -1. */
        private int dotAt(double x, double y) {
            int gx = (int) Math.round((x - left) / SPACING);
            int gy = (int) Math.round((y - top) / SPACING);
            if (!Rune.onGrid(gx, gy) || Math.hypot(x - (left + gx * SPACING), y - (top + gy * SPACING)) > 11) {
                return -1;
            }
            return Rune.dot(gx, gy);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (super.mouseClicked(x, y, button)) {
                return true;
            }
            int dot = dotAt(x, y);
            if (button == 0 && dot >= 0) {
                lastDot = dot;
                strokes.add(new LinkedHashSet<>());
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            mouseX = x;
            mouseY = y;
            if (lastDot >= 0 && button == 0) {
                int dot = dotAt(x, y);
                if (dot >= 0 && dot != lastDot && Rune.line(lastDot % Rune.GRID, lastDot / Rune.GRID, dot % Rune.GRID,
                        dot / Rune.GRID, strokes.get(strokes.size() - 1))) {
                    lastDot = dot;
                }
                return true;
            }
            return super.mouseDragged(x, y, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            if (lastDot >= 0 && !strokes.isEmpty() && strokes.get(strokes.size() - 1).isEmpty()) {
                strokes.remove(strokes.size() - 1);
            }
            lastDot = -1;
            return super.mouseReleased(x, y, button);
        }

        @Override
        public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
            super.render(graphics, mx, my, partialTick);
            int pad = 22;
            graphics.fill(left - pad - 2, top - pad - 2, left + SPACING * 4 + pad + 2, top + SPACING * 4 + pad + 2, PAPER_EDGE);
            graphics.fill(left - pad, top - pad, left + SPACING * 4 + pad, top + SPACING * 4 + pad, PAPER);
            grid(graphics, left, top, SPACING, drawn(), INK, 4);
            if (lastDot >= 0) {
                line(graphics, left + lastDot % Rune.GRID * SPACING, top + lastDot / Rune.GRID * SPACING, (float) mouseX,
                        (float) mouseY, 2, 0x803B2416);
            }
            graphics.drawCenteredString(font, title, width / 2, top - pad - 16, 0xFFFFFF);
            Rune match = Rune.match(drawn());
            Component status = match == null ? Component.translatable("tensurafragments.rune.canvas_hint")
                    : Component.translatable("tensurafragments.rune.canvas_match", Component.translatable(match.translationKey()));
            // At the foot of the sheet, under the dots.
            graphics.drawCenteredString(font, status, width / 2, top + SPACING * 4 + 9,
                    match == null ? 0xFF8C7650 : match.colour());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // ---- The Rune Codex ----

    /** Two runes a page: how each is drawn, and what it does on a creature and on a weapon. */
    public static final class Codex extends Screen {
        private static final int SPACING = 16;
        private final Screen back;
        private int page;

        Codex(Screen back) {
            super(Component.translatable("item.tensurafragments.rune_codex"));
            this.back = back;
        }

        private int pages() {
            return (Rune.values().length + 1) / 2;
        }

        @Override
        protected void init() {
            int y = height / 2 + 102;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                    .bounds(width / 2 - 130, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages() - 1, page + 1))
                    .bounds(width / 2 + 110, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                    .bounds(width / 2 - 40, y, 80, 20).build());
        }

        @Override
        public void onClose() {
            minecraft.setScreen(back);
        }

        @Override
        public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
            super.render(graphics, mx, my, partialTick);
            int bookLeft = width / 2 - 140;
            int bookTop = height / 2 - 100;
            graphics.fill(bookLeft - 2, bookTop - 2, bookLeft + 282, bookTop + 186, PAPER_EDGE);
            graphics.fill(bookLeft, bookTop, bookLeft + 280, bookTop + 184, PAPER);
            graphics.drawCenteredString(font, title, width / 2, bookTop - 14, 0xFFE9C46A);
            List<FormattedCharSequence> how = font.split(Component.translatable("tensurafragments.rune.codex_how"), 270);
            for (int i = 0; i < how.size(); i++) {
                graphics.drawString(font, how.get(i), bookLeft + 5, bookTop + 4 + i * 9, INK, false);
            }
            int top = bookTop + 6 + how.size() * 9;
            graphics.fill(width / 2 - 1, top, width / 2 + 1, bookTop + 180, 0x40000000);
            for (int i = 0; i < 2; i++) {
                int index = page * 2 + i;
                if (index < Rune.values().length) {
                    drawRune(graphics, Rune.values()[index], bookLeft + 6 + i * 140, top);
                }
            }
            graphics.drawCenteredString(font, (page + 1) + " / " + pages(), width / 2, bookTop + 189, 0xCCCCCC);
        }

        private void drawRune(GuiGraphics graphics, Rune rune, int left, int top) {
            graphics.drawString(font, Component.translatable(rune.translationKey()), left, top, darker(rune.colour()), false);
            int gridLeft = left + 34;
            int gridTop = top + 14;
            grid(graphics, gridLeft, gridTop, SPACING, rune.segments(), INK, 3);
            int y = gridTop + SPACING * 4 + 8;
            for (String side : new String[] {".creature", ".weapon"}) {
                for (FormattedCharSequence line : font.split(Component.translatable(rune.translationKey() + side), 128)) {
                    graphics.drawString(font, line, left, y, INK, false);
                    y += 9;
                }
                y += 3;
            }
        }

        /** Rune colours are bright; on paper they need to be darker to read. */
        private static int darker(int colour) {
            int r = (colour >> 16 & 0xFF) * 3 / 5;
            int g = (colour >> 8 & 0xFF) * 3 / 5;
            int b = (colour & 0xFF) * 3 / 5;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
