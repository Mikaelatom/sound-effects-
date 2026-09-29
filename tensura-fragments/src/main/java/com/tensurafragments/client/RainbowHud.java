package com.tensurafragments.client;

import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Bottom-left HUD for Rainbow Talismans, shown while it's on the active skill preset: the selected rainbow talisman
 * (colour-cycling) and your paper. Sits above the Shikigami Control HUD if both are equipped.
 */
public final class RainbowHud {
    private RainbowHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.RAINBOW_TALISMANS.get())) {
            return;
        }
        boolean shikigamiToo = EquippedSkills.isEquipped(mc.player, ModSkills.SHIKIGAMI_CONTROL.get());
        int x = 8;
        int y = graphics.guiHeight() - (shikigamiToo ? 90 : 40);
        Spell spell = ClientShikigamiState.rainbowSpell();
        float time = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        int colour = 0xFF000000 | Mth.hsvToRgb((time / 60F) % 1F, 0.55F, 1.0F);

        graphics.setColor(((colour >> 16) & 0xFF) / 255F, ((colour >> 8) & 0xFF) / 255F, (colour & 0xFF) / 255F, 1F);
        graphics.blit(TalismanRenderer.texture(spell), x + 4, y, 0, 0, 8, 16, 8, 16);
        graphics.setColor(1F, 1F, 1F, 1F);
        graphics.drawString(mc.font, Component.translatable("tensurafragments.spell.rainbow",
                Component.translatable("tensurafragments.spell." + spell.id())), x + 16, y + 5, colour, true);
        if (!shikigamiToo) {
            int paper = Paper.count(mc.player);
            int leaves = Paper.countLeaves(mc.player);
            String count = paper > 0 || leaves == 0 ? paper + " paper" : leaves + " leaves";
            graphics.drawString(mc.font, count, x + 16, y + 15, paper + leaves > 0 ? 0xFFCCCCCC : 0xFFFF6060, true);
        }
    }
}
