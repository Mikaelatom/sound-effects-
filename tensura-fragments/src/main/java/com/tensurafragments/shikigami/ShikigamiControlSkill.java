package com.tensurafragments.shikigami;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.MomentumTracker;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Shikigami Control as a Tensura skill. Modes:
 * <ol start="0">
 *   <li>Shikigami: turn the block you look at into a helper (sneak: dismiss all)</li>
 *   <li>Talisman: throw the selected spell talisman (sneak: switch spell)</li>
 *   <li>Barrier: plant a talisman anchor (sneak: raise or dispel the barrier)</li>
 *   <li>Substitution: turn the automatic paper-doll dodge on or off (on by default)</li>
 *   <li>Paper Beast: fold paper into the selected beast (sneak: switch beast)</li>
 *   <li>Possess: see through a paper beast and control it; press again to come back (sneak: unfold all beasts)</li>
 * </ol>
 * Magicules are charged by {@link ShikigamiControl} only when an action actually happens.
 */
public class ShikigamiControlSkill extends Skill {
    public static final int MODE_SHIKIGAMI = 0;
    public static final int MODE_TALISMAN = 1;
    public static final int MODE_BARRIER = 2;
    public static final int MODE_SUBSTITUTION = 3;
    public static final int MODE_BEAST = 4;
    public static final int MODE_POSSESS = 5;
    private static final String[] MODE_IDS = {"shikigami", "talisman", "barrier", "substitution", "beast", "possess"};

    public ShikigamiControlSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/shikigami_control.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_IDS.length;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode >= 0 && mode < MODE_IDS.length ? "shikigami_control." + MODE_IDS[mode] : super.getModeId(instance, mode);
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
        boolean sneaking = player.isShiftKeyDown();
        boolean used = switch (mode) {
            case MODE_SHIKIGAMI -> {
                if (sneaking) {
                    ShikigamiControl.dismissAll(player);
                    yield false;
                }
                yield ShikigamiControl.summon(player);
            }
            case MODE_TALISMAN -> {
                if (sneaking) {
                    ShikigamiControl.cycleSpell(player);
                    yield false;
                }
                yield ShikigamiControl.throwTalisman(player, MomentumTracker.velocity(player));
            }
            case MODE_BARRIER -> {
                if (sneaking) {
                    ShikigamiControl.toggleBarrier(player);
                    yield false;
                }
                yield ShikigamiControl.placeAnchor(player);
            }
            case MODE_SUBSTITUTION -> ShikigamiControl.toggleSubstitution(player);
            case MODE_BEAST -> {
                if (sneaking) {
                    PaperBeasts.cycleKind(player);
                    yield false;
                }
                yield PaperBeasts.fold(player);
            }
            case MODE_POSSESS -> {
                if (sneaking && PaperBeasts.possessed(player) == null) {
                    PaperBeasts.dismissAll(player);
                    yield false;
                }
                yield PaperBeasts.togglePossession(player);
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
