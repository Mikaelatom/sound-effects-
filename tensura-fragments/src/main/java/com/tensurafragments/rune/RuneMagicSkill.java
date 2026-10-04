package com.tensurafragments.rune;

import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Rune Magic as a Tensura skill: press it (with paper on you) to draw a rune. */
public class RuneMagicSkill extends Skill {
    public RuneMagicSkill() {
        super(SkillType.UNIQUE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/rune_magic.png");
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity instanceof ServerPlayer player && !player.isSpectator()) {
            RuneMagic.giveCodex(player);
            RuneMagic.startDrawing(player);
        }
    }

    // Not learnable through Tensura's normal routes; it's picked as a starting skill (or granted).
    @Override
    public double getAcquiringMagiculeCost(ManasSkillInstance instance) {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player entity, double newEP) {
        return false;
    }
}
