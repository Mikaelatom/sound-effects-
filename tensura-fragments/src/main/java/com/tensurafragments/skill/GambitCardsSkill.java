package com.tensurafragments.skill;

import com.tensurafragments.Config;
import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Gambit Cards as a Tensura skill. Use it with Tensura's skill keys; switch mode to pick the action:
 * <ol start="0">
 *   <li>Throw a card</li>
 *   <li>Teleport to a card</li>
 *   <li>Detonate cards (sneak to detonate only the aimed one)</li>
 * </ol>
 * The behaviour itself lives in {@link GambitCards}.
 */
public class GambitCardsSkill extends Skill {
    public static final int MODE_THROW = 0;
    public static final int MODE_TELEPORT = 1;
    public static final int MODE_DETONATE = 2;

    public GambitCardsSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/gambit_cards.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case MODE_THROW -> "gambit_cards.throw";
            case MODE_TELEPORT -> "gambit_cards.teleport";
            case MODE_DETONATE -> "gambit_cards.detonate";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }

    /** Tensura spends this when the key is pressed, so it is 0 whenever the action would do nothing. */
    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (!(entity instanceof ServerPlayer player)) {
            return 0;
        }
        return switch (mode) {
            case MODE_THROW -> GambitCards.getDeck(player) > 0 ? Config.THROW_MAGICULE_COST.get() : 0;
            case MODE_TELEPORT -> GambitCards.getCards(player).isEmpty() ? 0 : Config.TELEPORT_MAGICULE_COST.get();
            default -> 0;
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        switch (mode) {
            case MODE_THROW -> GambitCards.throwCard(player);
            case MODE_TELEPORT -> GambitCards.teleport(player);
            case MODE_DETONATE -> GambitCards.detonate(player, player.isShiftKeyDown());
            default -> {
                return;
            }
        }
        addMasteryPoint(instance, player);
    }

    // Not learnable through Tensura's normal routes; this addon grants it directly.
    @Override
    public double getAcquiringMagiculeCost(ManasSkillInstance instance) {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player entity, double newEP) {
        return false;
    }
}
