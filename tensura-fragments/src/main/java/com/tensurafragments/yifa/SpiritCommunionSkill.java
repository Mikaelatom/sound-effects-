package com.tensurafragments.yifa;

import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Spirit Communion (Yifa) as a Tensura skill. Modes:
 * <ol start="0">
 *   <li>Spirit Sight: see elemental spirits (and anything invisible) on / off</li>
 *   <li>Call Spirit: bind the spirit you look at (sneak: every spirit within 8 blocks)</li>
 *   <li>Spirit Magic: release every bound spirit as one spell, decided by the mix</li>
 *   <li>Spirit Jutsu: a weak fire or wind jutsu with no spirits (sneak: switch element)</li>
 * </ol>
 */
public class SpiritCommunionSkill extends Skill {
    public static final int MODE_SIGHT = 0;
    public static final int MODE_CALL = 1;
    public static final int MODE_MAGIC = 2;
    public static final int MODE_JUTSU = 3;
    private static final String[] MODE_IDS = {"sight", "call", "magic", "jutsu"};

    public SpiritCommunionSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/spirit_communion.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_IDS.length;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "spirit_communion." + MODE_IDS[Math.floorMod(mode, MODE_IDS.length)];
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODE_IDS.length);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        boolean used = switch (mode) {
            case MODE_SIGHT -> {
                SpiritCommunion.toggleSight(player);
                yield false;
            }
            case MODE_CALL -> SpiritCommunion.call(player);
            case MODE_MAGIC -> SpiritCommunion.release(player);
            case MODE_JUTSU -> {
                if (player.isShiftKeyDown()) {
                    SpiritCommunion.cycleJutsu(player);
                    yield false;
                }
                yield SpiritCommunion.jutsu(player);
            }
            default -> false;
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
