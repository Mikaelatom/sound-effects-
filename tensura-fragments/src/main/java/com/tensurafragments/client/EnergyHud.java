package com.tensurafragments.client;

import com.tensurafragments.energy.EnergyMagic;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Small HUD for Energy Magic, while it's on the active preset: the selected energy spell and its cost in levels, in
 * green just left of the hotbar (red when you don't have the levels).
 */
public final class EnergyHud {
    private EnergyHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.ENERGY_MAGIC.get())) {
            return;
        }
        var spell = ClientShikigamiState.energySpell();
        int cost = EnergyMagic.levelCost(spell);
        boolean affordable = mc.player.getAbilities().instabuild || mc.player.experienceLevel >= cost;
        Component text = Component.translatable("tensurafragments.energy.hud",
                Component.translatable("tensurafragments.energy.spell." + spell.id()), cost);
        int x = graphics.guiWidth() / 2 - 96 - mc.font.width(text);
        int y = graphics.guiHeight() - 12;
        graphics.drawString(mc.font, text, x, y, affordable ? 0xFF7CFF6A : 0xFFFF6060, true);
    }
}
