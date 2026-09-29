package com.tensurafragments.client;

import com.tensurafragments.shikigami.BarrierAnchorEntity;
import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bottom-left HUD for Shikigami Control, shown while it's on the active skill preset: paper count (or leaves once
 * the paper runs out), the Substitution doll (glowing while automatic dodging is on,
 * with a bar while on cooldown), one timer bar per shikigami, and barrier anchors placed.
 */
public final class ShikigamiHud {
    private static final ItemStack PAPER = new ItemStack(Items.PAPER);
    private static final ItemStack LEAVES = new ItemStack(Items.OAK_LEAVES);

    private ShikigamiHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.SHIKIGAMI_CONTROL.get())) {
            return;
        }
        int x = 8;
        int y = graphics.guiHeight() - 40;

        // Paper, with a glow behind it while automatic Substitution is on.
        int paper = Paper.count(mc.player);
        int leaves = Paper.countLeaves(mc.player);
        // Show leaves in place of paper once the paper runs out, since that's what will be used next.
        boolean usingLeaves = paper == 0 && leaves > 0;
        if (ClientShikigamiState.isEnabled() && (paper > 0 || leaves > 0)) {
            graphics.fill(x - 2, y - 2, x + 18, y + 18, usingLeaves ? 0x6096E078 : 0x60FFE08A);
        }
        graphics.renderItem(usingLeaves ? LEAVES : PAPER, x, y);
        int shown = usingLeaves ? leaves : paper;
        graphics.drawString(mc.font, String.valueOf(shown), x + 18, y + 5,
                shown > 0 ? (usingLeaves ? 0xFF9BE07C : 0xFFFFFFFF) : 0xFFFF6060, true);
        float cooldown = ClientShikigamiState.cooldownFraction();
        if (cooldown > 0) {
            graphics.fill(x, y + 17, x + Math.round(16 * cooldown), y + 19, 0xFFB0B0B0);
        }

        // The selected spell talisman, next to the paper count.
        Spell spell = ClientShikigamiState.spell();
        int sx = x + 30;
        graphics.blit(TalismanRenderer.texture(spell), sx, y, 0, 0, 8, 16, 8, 16);
        graphics.drawString(mc.font, Component.translatable("tensurafragments.spell." + spell.id()), sx + 11, y + 5,
                spell.colour(), true);

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
            graphics.drawString(mc.font, text, x + 44, y - 8 - row * 6 - 4, barrierUp ? 0xFFFFE08A : 0xFFCCCCCC, true);
        }
    }
}
