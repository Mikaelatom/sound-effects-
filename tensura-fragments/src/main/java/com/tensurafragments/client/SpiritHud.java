package com.tensurafragments.client;

import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.yifa.WispEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * HUD for Spirit Communion, while it's on the active preset (middle of the left edge): an eye while Spirit
 * Sight is on, your bound spirits as coloured orbs (fading as their time runs out), and the jutsu element.
 */
public final class SpiritHud {
    private SpiritHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.SPIRIT_COMMUNION.get())) {
            return;
        }
        int x = 8;
        int y = graphics.guiHeight() / 2 - 20;
        graphics.drawString(mc.font, ClientSpiritSight.hasSight() ? "◉" : "○", x, y, ClientSpiritSight.hasSight()
                ? 0xFFB8F5D8 : 0xFF808080, true);
        int ox = x + 12;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof WispEntity wisp && wisp.isBoundTo(mc.player)) {
                graphics.blit(WispRenderer.texture(wisp.getElement()), ox, y - 2, 0, 0, 12, 12, 12, 12);
                float left = Math.min(1F, wisp.getTimeLeft() / 600F);
                graphics.fill(ox, y + 11, ox + Math.round(12 * left), y + 12, 0xFFE8E0C0);
                ox += 14;
            }
        }
        graphics.drawString(mc.font, Component.translatable("tensurafragments.yifa.jutsu_hud",
                        Component.translatable("tensurafragments.yifa.element." + (ClientSpiritSight.jutsu() == 1 ? "wind" : "fire"))),
                x, y + 14, 0xFFCCCCCC, true);
    }
}
