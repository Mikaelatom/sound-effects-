package com.tensurafragments.rainbow;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.spirit.SpiritControl;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Rainbow Magic as a Tensura skill. Modes: cast the selected rainbow spell (sneak: switch spell), and summon the next
 * spirit recoloured in rainbow at your aim.
 */
public class RainbowMagicSkill extends Skill {
    public RainbowMagicSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/rainbow_magic.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 1 ? "rainbow_magic.spirit" : "rainbow_magic.cast";
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 2);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (mode == 1) {
            if (SpiritControl.callRainbowAtAim(player)) {
                addMasteryPoint(instance, player);
            }
        } else if (player.isShiftKeyDown()) {
            RainbowMagic.cycleSpell(player);
        } else if (RainbowMagic.cast(player)) {
            addMasteryPoint(instance, player);
        }
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
