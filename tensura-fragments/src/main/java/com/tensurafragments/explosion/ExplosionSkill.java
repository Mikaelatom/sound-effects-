package com.tensurafragments.explosion;

import com.tensurafragments.Config;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.ExplosionMoves;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Explosion as a Tensura skill: explosions from your palms. It unlocks Combat Mode's Explosion fighting style (blast
 * punches, rising blast, explosive dive, grab and detonate, burst dash, hovering on blasts), and its key fires:
 * <ol start="0">
 *   <li>AP Shot: a focused blast that flies straight to what you aim at (long range)</li>
 *   <li>Stun Grenade: a blinding flash in front of you that stuns everything it catches</li>
 *   <li>Howitzer Impact: launch up and forward spinning, then a huge explosion (long cooldown)</li>
 * </ol>
 */
public class ExplosionSkill extends Skill {
    public static final int MODE_AP_SHOT = 0;
    public static final int MODE_STUN_GRENADE = 1;
    public static final int MODE_HOWITZER = 2;
    private static final int[] COOLDOWN_SECONDS = {2, 8, 30};

    public ExplosionSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/explosion.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case MODE_STUN_GRENADE -> "explosion.stun_grenade";
            case MODE_HOWITZER -> "explosion.howitzer";
            default -> "explosion.ap_shot";
        };
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator() || mode < 0 || mode > 2
                || instance.getCoolDown(mode) > 0) {
            return;
        }
        boolean used = switch (mode) {
            case MODE_STUN_GRENADE -> ExplosionMoves.stunGrenade(player);
            case MODE_HOWITZER -> ExplosionMoves.howitzer(player);
            default -> ExplosionMoves.apShot(player);
        };
        if (used) {
            instance.setCoolDown(COOLDOWN_SECONDS[mode], mode);
            addMasteryPoint(instance, player);
        }
    }

    /** Gives the skill to a player who doesn't have it yet (when the grant option is on). */
    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_EXPLOSION.get() && !ExplosionMoves.hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.EXPLOSION.get());
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
