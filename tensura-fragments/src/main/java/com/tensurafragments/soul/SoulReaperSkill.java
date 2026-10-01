package com.tensurafragments.soul;

import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Soul Reaper as a Tensura skill. Modes: Soul Summon, Soul Absorb, Soul Possession. Sneak with any mode to switch
 * which captured soul is selected. Every kill gives souls whatever mode you're in.
 */
public class SoulReaperSkill extends Skill {
    private static final String[] MODES = {"soul_reaper.summon", "soul_reaper.absorb", "soul_reaper.possess"};

    public SoulReaperSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/soul_reaper.png");
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
        if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (player.isShiftKeyDown()) {
            SoulReaper.cycle(player);
            return;
        }
        boolean used = switch (Math.floorMod(mode, MODES.length)) {
            case 0 -> SoulReaper.summon(player);
            case 1 -> SoulReaper.absorb(player);
            default -> SoulReaper.possess(player);
        };
        if (used) {
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
