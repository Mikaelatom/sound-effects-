package com.tensurafragments.ally;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/** A tamed summon's only target goal: whatever its owner last hit (see {@link Allies#target}), and nothing else. */
public class OwnersTargetGoal extends TargetGoal {
    private final TamableAnimal summon;
    private LivingEntity chosen;

    public OwnersTargetGoal(TamableAnimal summon) {
        super(summon, false);
        this.summon = summon;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    private LivingEntity ownersTarget() {
        return summon.getOwner() instanceof ServerPlayer owner ? Allies.target(owner) : null;
    }

    @Override
    public boolean canUse() {
        chosen = ownersTarget();
        return chosen != null && chosen != summon && canAttack(chosen, TargetingConditions.DEFAULT);
    }

    @Override
    public boolean canContinueToUse() {
        return summon.getTarget() != null && summon.getTarget() == ownersTarget();
    }

    @Override
    public void start() {
        summon.setTarget(chosen);
        super.start();
    }
}
