package com.tensurafragments.grimoire;

import com.tensurafragments.TensuraFragments;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Sealing Grimoire as a Tensura skill. Modes:
 * <ol start="0">
 *   <li>Seal Creature: seal the weakened creature you're looking at (1 paper)</li>
 *   <li>Seal Magic: open the book for a moment to catch incoming magic or projectiles (1 paper per catch)</li>
 *   <li>Release: summon the selected page (sneak: turn to the next page)</li>
 * </ol>
 */
public class SealingGrimoireSkill extends Skill {
    public static final int MODE_SEAL_CREATURE = 0;
    public static final int MODE_SEAL_MAGIC = 1;
    public static final int MODE_RELEASE = 2;
    private static final String[] MODE_IDS = {"seal_creature", "seal_magic", "release"};

    public SealingGrimoireSkill() {
        super(SkillType.UNIQUE);
    }

    /** Tensura's default icon lookup always uses the "tensura" namespace, so point it at our own texture. */
    @Override
    public ResourceLocation getSkillIcon() {
        return TensuraFragments.id("textures/skill/unique/sealing_grimoire.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_IDS.length;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode >= 0 && mode < MODE_IDS.length ? "sealing_grimoire." + MODE_IDS[mode] : super.getModeId(instance, mode);
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
            case MODE_SEAL_CREATURE -> SealingGrimoire.sealCreature(player);
            case MODE_SEAL_MAGIC -> SealingGrimoire.readyCatch(player);
            case MODE_RELEASE -> {
                if (player.isShiftKeyDown()) {
                    SealingGrimoire.cyclePage(player);
                    yield false;
                }
                yield SealingGrimoire.release(player);
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
