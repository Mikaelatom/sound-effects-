package com.tensurafragments.skill;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.energy.EnergyMagic;
import com.tensurafragments.flame.FlameEmperor;
import com.tensurafragments.grimoire.SealingGrimoire;
import com.tensurafragments.magic.SpellLearning;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.shikigami.PaperBeasts;
import com.tensurafragments.shikigami.ShikigamiControl;
import com.tensurafragments.soul.SoulReaper;
import com.tensurafragments.spirit.SpiritControl;
import com.tensurafragments.yifa.SpiritCommunion;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
        PaperBeasts.tick(player);
        SpiritCommunion.tick(player);
        if (player.tickCount % 40 == 0) {
            OriginalSkillStripper.strip(player);
            GambitCards.grantSkill(player);
            ShikigamiControl.grantSkill(player);
            SealingGrimoire.grantSkill(player);
            RainbowMagic.grantSkill(player);
            FlameEmperor.grantSkill(player);
            SpiritControl.grantSkill(player);
            SpiritCommunion.grantSkill(player);
            EnergyMagic.grantSkill(player);
            SoulReaper.grantSkill(player);
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

    /** Your body was hurt while you were seeing through a paper beast: back you go. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
            PaperBeasts.onOwnerHurt(player);
            SpellLearning.onSurvivedHit(player, event.getSource());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PaperBeasts.forget(player);
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
            SpiritControl.grantSkill(player);
            SpiritCommunion.grantSkill(player);
            EnergyMagic.grantSkill(player);
            SoulReaper.grantSkill(player);
            GambitCards.sync(player);
            ShikigamiControl.sync(player);
            SpiritCommunion.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PaperBeasts.release(player);
            GambitCards.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PaperBeasts.release(player);
            GambitCards.sync(player);
        }
    }
}
