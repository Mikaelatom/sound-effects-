package com.tensurafragments.client;

import com.tensurafragments.rainbow.RainbowSpell;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Bottom-left HUD for Rainbow Magic, shown while it's on the active skill preset: the selected spell, colour-cycling.
 * Sits above the Shikigami Control HUD if both are equipped.
 */
public final class RainbowHud {
    private RainbowHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.RAINBOW_MAGIC.get())) {
            return;
        }
        boolean shikigamiToo = EquippedSkills.isEquipped(mc.player, ModSkills.SHIKIGAMI_CONTROL.get());
        int x = 8;
        int y = graphics.guiHeight() - (shikigamiToo ? 90 : 40);
        RainbowSpell spell = ClientShikigamiState.rainbowSpell();
        float time = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        Component name = Component.translatable("tensurafragments.rainbow.spell." + spell.id());
        // Each letter a different colour, shifting over time.
        String text = "\u2726 " + name.getString();
        int dx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int colour = 0xFF000000 | Mth.hsvToRgb(((time / 40F) + i * 0.06F) % 1F, 0.6F, 1.0F);
            graphics.drawString(mc.font, ch, dx, y + 5, colour, true);
            dx += mc.font.width(ch);
        }
    }
}
