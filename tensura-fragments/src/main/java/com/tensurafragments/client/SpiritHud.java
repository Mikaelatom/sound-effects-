package com.tensurafragments.client;

import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.yifa.WispEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

/**
 * Small HUD for Spirit Communion, while it's on the active preset: a row of your bound spirits (with a thin bar for
 * their time left) just right of the hotbar, led by a small dot that lights up while Spirit Sight is on.
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
        int x = graphics.guiWidth() / 2 + 96;
        int y = graphics.guiHeight() - 12;
        graphics.fill(x, y + 2, x + 3, y + 5, ClientSpiritSight.hasSight() ? 0xFFB8F5D8 : 0x80606060);
        int ox = x + 6;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof WispEntity wisp && wisp.isBoundTo(mc.player)) {
                graphics.blit(WispRenderer.texture(wisp.getElement()), ox, y, 0, 0, 8, 8, 8, 8);
                float left = Math.min(1F, wisp.getTimeLeft() / 600F);
                graphics.fill(ox, y + 9, ox + Math.round(8 * left), y + 10, 0xC0E8E0C0);
                ox += 10;
            }
        }
    }
}
