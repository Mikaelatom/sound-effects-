package com.tensurafragments.client;

import com.tensurafragments.card.CardEntity;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Bottom-right HUD, shown while Gambit Cards is on the active skill preset: the deck (filled = ready, outline = spent, with a bar for the next draw) and a row of timers
 * for the cards currently out. A timer turns gold when its card is fully charged.
 */
public final class CardHud {
    private static final int CARD_W = 7;
    private static final int CARD_H = 10;
    private static final int GAP = 2;

    private CardHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()
                || !EquippedSkills.isEquipped(mc.player, ModSkills.GAMBIT_CARDS.get())) {
            return;
        }
        int size = ClientDeckState.deckSize();
        int deck = ClientDeckState.deck();
        int width = size * (CARD_W + GAP) - GAP;
        int x0 = graphics.guiWidth() - width - 8;
        int y0 = graphics.guiHeight() - CARD_H - 22;

        for (int i = 0; i < size; i++) {
            int x = x0 + i * (CARD_W + GAP);
            if (i < deck) {
                graphics.fill(x, y0, x + CARD_W, y0 + CARD_H, 0xFFF2E6FF);
                graphics.fill(x + 1, y0 + 1, x + CARD_W - 1, y0 + CARD_H - 1, 0xFF3A2A6B);
                graphics.fill(x + 3, y0 + 4, x + 4, y0 + 6, 0xFFFFD34D);
            } else {
                graphics.renderOutline(x, y0, CARD_W, CARD_H, 0x80FFFFFF);
                if (i == deck) {
                    int filled = Math.round(ClientDeckState.regenFraction() * (CARD_H - 2));
                    graphics.fill(x + 1, y0 + CARD_H - 1 - filled, x + CARD_W - 1, y0 + CARD_H - 1, 0x80B39DFF);
                }
            }
        }

        // The spell Inscribe makes, left of the deck.
        var spell = ClientDeckState.spellCard();
        Component inscribe = Component.translatable("tensurafragments.spell_card.hud",
                Component.translatable("item.tensurafragments." + spell.id() + "_card"),
                com.tensurafragments.card.SpellCards.cost(spell));
        graphics.drawString(mc.font, inscribe, x0 - mc.font.width(inscribe) - 6, y0 + 1, 0xFF000000 | spell.colour(), true);

        List<CardEntity> cards = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CardEntity card && card.getOwner() == mc.player) {
                cards.add(card);
            }
        }
        cards.sort(Comparator.comparingInt(CardEntity::getTicksLeft));

        int y = y0 - 6;
        for (int i = 0; i < cards.size(); i++) {
            CardEntity card = cards.get(i);
            float left = card.getTicksLeft() / (float) Math.max(1, card.getLifetime());
            int barWidth = Math.round(width * left);
            int color = card.isFullyCharged() ? 0xFFFFD34D : 0xFFB39DFF;
            int by = y - i * 4;
            graphics.fill(x0, by, x0 + width, by + 2, 0x60000000);
            graphics.fill(x0, by, x0 + barWidth, by + 2, color);
        }
    }
}
