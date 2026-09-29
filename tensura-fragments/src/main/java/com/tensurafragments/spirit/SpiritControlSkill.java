package com.tensurafragments.spirit;

import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Spirit Control as a Tensura skill. While it's equipped, each of your melee hits calls the next spirit onto the
 * target. The key sends a spirit at whatever you aim at; sneak + key turns Spirit Link (the melee part) on or off.
 */
public class SpiritControlSkill extends Skill {
    public SpiritControlSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/spirit_control.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "spirit_control.call";
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (player.isShiftKeyDown()) {
            SpiritControl.toggleLink(player);
        } else if (SpiritControl.callAtAim(player)) {
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
