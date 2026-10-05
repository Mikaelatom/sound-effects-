package com.tensurafragments.rainbow;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.RainbowEntityPayload;
import com.tensurafragments.shikigami.Spell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side of Rainbow Magic: the all-element hit, the rainbow burst when a spell ends, and telling clients. */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class RainbowEvents {
    private RainbowEvents() {
    }

    /** A rainbow spell's hit also carries every element's damage and effects. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !RainbowMagic.isRainbow(direct) || target == event.getSource().getEntity()) {
            return;
        }
        event.setAmount(event.getAmount()
                + RainbowMagic.prismDamage() * com.tensurafragments.skill.EpScaling.multiplier(event.getSource().getEntity()));
        RainbowMagic.applyElements(target);
    }

    /** When a rainbow spell ends (hits, explodes or fades) it bursts into a rainbow. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel() instanceof ServerLevel level && RainbowMagic.isRainbow(entity)
                && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
            Spell.rainbowBurst(level, entity.position(), 2.0);
        }
    }

    /** Players who come into range of a rainbow spell need to know to draw it in rainbow. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && RainbowMagic.isRainbow(event.getTarget())) {
            PacketDistributor.sendToPlayer(player, new RainbowEntityPayload(event.getTarget().getId()));
        }
    }
}
