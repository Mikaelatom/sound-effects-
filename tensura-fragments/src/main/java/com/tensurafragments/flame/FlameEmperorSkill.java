package com.tensurafragments.flame;

import com.tensurafragments.Config;
import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Flame Emperor as a Tensura Ultimate skill. Modes:
 * <ol start="0">
 *   <li>Fire Magic: cast the selected Tensura fire spell (sneak: switch spell)</li>
 *   <li>Draconic Hell Storm: a magic circle on your hand, then Gluttony's mist as hellfire (long cooldown)</li>
 * </ol>
 */
public class FlameEmperorSkill extends Skill {
    public static final int MODE_FIRE_MAGIC = 0;
    public static final int MODE_HELL_STORM = 1;

    public FlameEmperorSkill() {
        super(SkillType.ULTIMATE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/ultimate/flame_emperor.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == MODE_HELL_STORM ? "flame_emperor.hell_storm" : "flame_emperor.fire_magic";
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return mode == MODE_FIRE_MAGIC ? MODE_HELL_STORM : MODE_FIRE_MAGIC;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (mode == MODE_HELL_STORM) {
            if (instance.getCoolDown(MODE_HELL_STORM) > 0) {
                return;
            }
            if (FlameEmperor.hellStorm(player)) {
                instance.setCoolDown(Config.HELL_STORM_COOLDOWN_SECONDS.get(), MODE_HELL_STORM);
                addMasteryPoint(instance, player);
            }
        } else if (player.isShiftKeyDown()) {
            FlameEmperor.cycleSpell(player);
        } else if (FlameEmperor.castFire(player)) {
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
