package com.tensurafragments.client;

import com.tensurafragments.flame.FireSpell;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Bottom-right HUD for Flame Emperor, shown while it's on the active skill preset: the selected fire spell. */
public final class FlameHud {
    private FlameHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.FLAME_EMPEROR.get())) {
            return;
        }
        boolean cardsToo = EquippedSkills.isEquipped(mc.player, ModSkills.GAMBIT_CARDS.get());
        FireSpell spell = ClientShikigamiState.fireSpell();
        String text = "🔥 " + Component.translatable("tensurafragments.flame.spell." + spell.id()).getString();
        // Flickers between orange and yellow like a flame.
        float flicker = 0.5F + 0.5F * Mth.sin((mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false)) * 0.4F);
        int colour = 0xFF000000 | Mth.hsvToRgb(0.03F + 0.07F * flicker, 0.85F, 1.0F);
        int x = graphics.guiWidth() - mc.font.width(text) - 8;
        int y = graphics.guiHeight() - (cardsToo ? 56 : 30);
        graphics.drawString(mc.font, text, x, y, colour, true);
    }
}
