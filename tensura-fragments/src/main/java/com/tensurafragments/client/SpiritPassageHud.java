package com.tensurafragments.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A spirit's clocks, centred above the hotbar: time left to take a body in the material world (red in the last 30
 * seconds), and an active Spirit Release.
 */
public final class SpiritPassageHud {
    /** Client game time the spirit fades at, or 0. */
    private static long fadesAt;
    private static int releasePercent;
    /** Client game time the release ends at. */
    private static long releaseEndsAt;

    private SpiritPassageHud() {
    }

    public static void sync(int ticksLeft, int percent, int releaseTicksLeft) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        fadesAt = ticksLeft <= 0 || mc.level == null ? 0 : now + ticksLeft;
        releasePercent = percent;
        releaseEndsAt = percent <= 0 ? 0 : now + releaseTicksLeft;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        if (releaseEndsAt != 0) {
            long releaseLeft = Math.max(0, releaseEndsAt - mc.level.getGameTime()) / 20;
            Component release = Component.translatable("tensurafragments.spirit_release.hud", releasePercent,
                    releaseLeft / 60, String.format("%02d", releaseLeft % 60));
            graphics.drawString(mc.font, release, (graphics.guiWidth() - mc.font.width(release)) / 2,
                    graphics.guiHeight() - 99, 0xFF7FD8FF, true);
        }
        if (fadesAt == 0) {
            return;
        }
        long left = Math.max(0, fadesAt - mc.level.getGameTime()) / 20;
        Component text = Component.translatable("tensurafragments.spirit_race.hud", left / 60, String.format("%02d", left % 60));
        int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
        graphics.drawString(mc.font, text, x, graphics.guiHeight() - 88, left <= 30 ? 0xFFFF7070 : 0xFF9FD8FF, true);
    }
}
