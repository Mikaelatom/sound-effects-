package com.tensurafragments.skill;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.flame.FlameEmperor;
import com.tensurafragments.grimoire.SealingGrimoire;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.shikigami.ShikigamiControl;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class SkillEvents {
    private SkillEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MomentumTracker.tick(player);
        GambitCards.tickDeck(player);
        if (player.tickCount % 40 == 0) {
            OriginalSkillStripper.strip(player);
            GambitCards.grantSkill(player);
            ShikigamiControl.grantSkill(player);
            SealingGrimoire.grantSkill(player);
            RainbowMagic.grantSkill(player);
            FlameEmperor.grantSkill(player);
        }
    }

    /** While Shikigami Control's Substitution is on, a paper doll takes the hit instead of you. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            float getsThrough = ShikigamiControl.trySubstitute(player, event.getSource());
            if (getsThrough <= 0) {
                event.setCanceled(true);
            } else if (getsThrough < 1) {
                event.setAmount(event.getAmount() * getsThrough);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OriginalSkillStripper.strip(player);
            GambitCards.grantSkill(player);
            ShikigamiControl.grantSkill(player);
            SealingGrimoire.grantSkill(player);
            RainbowMagic.grantSkill(player);
            FlameEmperor.grantSkill(player);
            GambitCards.sync(player);
            ShikigamiControl.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GambitCards.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GambitCards.sync(player);
        }
    }
}
