package com.tensurafragments.client;

import com.tensurafragments.shikigami.BarrierAnchorEntity;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bottom-left HUD for Shikigami Control: paper count, the Substitution doll (bright while the window is open,
 * greyed with a bar while on cooldown), one timer bar per shikigami, and barrier anchors placed.
 */
public final class ShikigamiHud {
    private static final ItemStack PAPER = new ItemStack(Items.PAPER);

    private ShikigamiHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || SkillAPI.getSkillsFrom(mc.player).getSkill(ModSkills.SHIKIGAMI_CONTROL.getId()).isEmpty()) {
            return;
        }
        int x = 8;
        int y = graphics.guiHeight() - 40;

        // Paper, with the Substitution state drawn behind it.
        if (ClientShikigamiState.isWindowOpen()) {
            graphics.fill(x - 2, y - 2, x + 18, y + 18, 0xA0FFE08A);
        }
        graphics.renderItem(PAPER, x, y);
        int paper = mc.player.getInventory().countItem(Items.PAPER);
        graphics.drawString(mc.font, String.valueOf(paper), x + 18, y + 5, paper > 0 ? 0xFFFFFFFF : 0xFFFF6060, true);
        float cooldown = ClientShikigamiState.cooldownFraction();
        if (cooldown > 0 && !ClientShikigamiState.isWindowOpen()) {
            graphics.fill(x, y + 17, x + Math.round(16 * cooldown), y + 19, 0xFFB0B0B0);
        }

        int anchors = 0;
        boolean barrierUp = false;
        int row = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ShikigamiEntity shikigami && mc.player.getUUID().equals(shikigami.getOwnerUUID())) {
                float left = shikigami.getTicksLeft() / (float) Math.max(1, shikigami.getLifetime());
                float health = shikigami.getHealth() / shikigami.getMaxHealth();
                int by = y - 8 - row * 6;
                graphics.fill(x, by, x + 40, by + 2, 0x60000000);
                graphics.fill(x, by, x + Math.round(40 * health), by + 2, 0xFF7CE07C);
                graphics.fill(x, by + 2, x + Math.round(40 * left), by + 3, 0xFFE8D8A0);
                row++;
            } else if (entity instanceof BarrierAnchorEntity anchor && anchor.isOwnedBy(mc.player)) {
                anchors++;
                barrierUp |= anchor.isActive();
            }
        }
        if (anchors > 0) {
            String text = (barrierUp ? "◆ " : "◇ ") + anchors;
            graphics.drawString(mc.font, text, x + 44, y + 5, barrierUp ? 0xFFFFE08A : 0xFFCCCCCC, true);
        }
    }
}
