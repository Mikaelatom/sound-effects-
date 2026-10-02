package com.tensurafragments.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A spirit's clock in the material world: time left to take a body, centred above the hotbar, turning red in the
 * last 30 seconds.
 */
public final class SpiritPassageHud {
    /** Client game time the spirit fades at, or 0. */
    private static long fadesAt;

    private SpiritPassageHud() {
    }

    public static void sync(int ticksLeft) {
        Minecraft mc = Minecraft.getInstance();
        fadesAt = ticksLeft <= 0 || mc.level == null ? 0 : mc.level.getGameTime() + ticksLeft;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (fadesAt == 0 || mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        long left = Math.max(0, fadesAt - mc.level.getGameTime()) / 20;
        Component text = Component.translatable("tensurafragments.spirit_race.hud", left / 60, String.format("%02d", left % 60));
        int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
        graphics.drawString(mc.font, text, x, graphics.guiHeight() - 88, left <= 30 ? 0xFFFF7070 : 0xFF9FD8FF, true);
    }
}
