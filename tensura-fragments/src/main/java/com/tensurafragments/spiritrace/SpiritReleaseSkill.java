package com.tensurafragments.spiritrace;

import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Spirit Release, the spirits' own intrinsic skill. Modes: 10%, 50%, 100%. Press again to end it early. */
public class SpiritReleaseSkill extends Skill {
    private static final String[] MODES = {"spirit_release.ten", "spirit_release.fifty", "spirit_release.full"};

    public SpiritReleaseSkill() {
        super(SkillType.INTRINSIC);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/intrinsic/spirit_release.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES.length;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return MODES[Math.floorMod(mode, MODES.length)];
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES.length);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity instanceof ServerPlayer player && !player.isSpectator() && SpiritRelease.press(player, instance, mode)) {
            addMasteryPoint(instance, player);
        }
    }

    // Only spirits have it, as a race skill.
    @Override
    public double getAcquiringMagiculeCost(ManasSkillInstance instance) {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player entity, double newEP) {
        return false;
    }
}
