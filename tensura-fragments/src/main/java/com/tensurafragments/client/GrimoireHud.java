package com.tensurafragments.client;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.grimoire.GrimoireContents;
import com.tensurafragments.grimoire.SealingGrimoireItem;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Right-side HUD, shown while Sealing Grimoire is on the active skill preset: the book's pages, with the selected
 * one highlighted (green = creature, blue = magic).
 */
public final class GrimoireHud {
    private GrimoireHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.SEALING_GRIMOIRE.get())) {
            return;
        }
        ItemStack grimoire = ItemStack.EMPTY;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModRegistries.SEALING_GRIMOIRE.get())) {
                grimoire = inventory.getItem(i);
                break;
            }
        }
        int x = graphics.guiWidth() - 8;
        int y = graphics.guiHeight() / 2 - 40;
        if (grimoire.isEmpty()) {
            return;
        }
        graphics.renderItem(grimoire, x - 16, y - 18);
        GrimoireContents contents = SealingGrimoireItem.contents(grimoire);
        for (int i = 0; i < contents.pages().size(); i++) {
            GrimoireContents.Page page = contents.pages().get(i);
            boolean selected = i == contents.selected();
            int colour = page.kind() == GrimoireContents.Kind.CREATURE ? 0xFF9BE07C : 0xFF8FD3FF;
            String text = (selected ? "▶ " : "") + page.name();
            int width = mc.font.width(text);
            if (selected) {
                graphics.fill(x - width - 3, y - 1, x + 1, y + 9, 0x80000000);
            }
            graphics.drawString(mc.font, text, x - width, y, selected ? colour : (colour & 0x00FFFFFF) | 0xA0000000, true);
            y += 10;
        }
    }
}
