package com.tensurafragments.spirit;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.EquippedSkills;
import com.tensurafragments.skill.ModSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class SpiritEvents {
    private SpiritEvents() {
    }

    /** With Spirit Control equipped, every melee hit calls the next spirit onto the target. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity target
                && EquippedSkills.isEquipped(player, ModSkills.SPIRIT_CONTROL.get())) {
            SpiritControl.onMeleeHit(player, target);
        }
    }
}
