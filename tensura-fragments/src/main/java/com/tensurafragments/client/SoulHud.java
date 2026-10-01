package com.tensurafragments.client;

import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.soul.CapturedSoul;
import com.tensurafragments.soul.SoulReaper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Small HUD for Soul Reaper, while it's on the active preset: your souls and the selected captured soul, in soul blue
 * just right of the hotbar (above Spirit Communion's orbs when that's equipped too).
 */
public final class SoulHud {
    private SoulHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.SOUL_REAPER.get())) {
            return;
        }
        CapturedSoul soul = ClientSouls.selected();
        String souls = SoulReaper.format(ClientSouls.points());
        Component text = soul == null ? Component.translatable("tensurafragments.soul.hud_empty", souls)
                : Component.translatable("tensurafragments.soul.hud", souls, soul.name(), SoulReaper.epText(soul.ep()));
        int x = graphics.guiWidth() / 2 + 96;
        boolean spiritOrbs = EquippedSkills.isEquipped(mc.player, ModSkills.SPIRIT_COMMUNION.get());
        int y = graphics.guiHeight() - (spiritOrbs ? 23 : 12);
        graphics.drawString(mc.font, text, x, y, 0xFF000000 | SoulReaper.SOUL_COLOUR, true);
    }
}
