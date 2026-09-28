package com.mikaelatom.tensuragacha.skill;

import com.mikaelatom.tensuragacha.TensuraGacha;
import com.mikaelatom.tensuragacha.registry.ModSounds;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Unique Skill: Gambler. Pull the lever and spin three reels.
 * Three of a kind is a big buff, a pair is a small one, no match is a bust.
 * Higher mastery makes the buffs last longer.
 */
public class GamblerSkill extends Skill {
    private static final ResourceLocation ICON = TensuraGacha.id("textures/skill/gambler.png");
    private static final int COOLDOWN = 30;

    private record Reel(String symbol, Holder<MobEffect> effect, ChatFormatting color) {
    }

    private static final Reel[] REELS = {
            new Reel("⚔", MobEffects.DAMAGE_BOOST, ChatFormatting.RED),
            new Reel("❤", MobEffects.REGENERATION, ChatFormatting.LIGHT_PURPLE),
            new Reel("⛨", MobEffects.DAMAGE_RESISTANCE, ChatFormatting.BLUE),
            new Reel("➶", MobEffects.MOVEMENT_SPEED, ChatFormatting.AQUA),
            new Reel("7", MobEffects.ABSORPTION, ChatFormatting.GOLD),
    };

    public GamblerSkill() {
        super(SkillType.UNIQUE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ICON;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int slot, int mode) {
        if (!(entity instanceof ServerPlayer player)) return;

        RandomSource random = player.getRandom();
        Reel a = REELS[random.nextInt(REELS.length)];
        Reel b = REELS[random.nextInt(REELS.length)];
        Reel c = REELS[random.nextInt(REELS.length)];

        double masteryRatio = instance.getMaxMastery() > 0 ? Math.min(1.0, instance.getMastery() / instance.getMaxMastery()) : 0;
        int seconds = (int) (20 + 40 * masteryRatio);

        Component result;
        if (a == b && b == c) {
            player.addEffect(new MobEffectInstance(a.effect(), seconds * 20, 2));
            result = Component.translatable("tensuragacha.skill.gambler.triple").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            player.level().playSound(null, player.blockPosition(), ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 1.0F, 0.5F);
        } else if (a == b || b == c || a == c) {
            Reel pair = (a == b || a == c) ? a : b;
            player.addEffect(new MobEffectInstance(pair.effect(), seconds * 20, 0));
            result = Component.translatable("tensuragacha.skill.gambler.pair").withStyle(ChatFormatting.GREEN);
            player.level().playSound(null, player.blockPosition(), ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
            result = Component.translatable("tensuragacha.skill.gambler.bust").withStyle(ChatFormatting.DARK_GRAY);
        }

        player.displayClientMessage(Component.literal("[ ")
                .append(Component.literal(a.symbol()).withStyle(a.color())).append(" | ")
                .append(Component.literal(b.symbol()).withStyle(b.color())).append(" | ")
                .append(Component.literal(c.symbol()).withStyle(c.color())).append(" ]  ")
                .append(result), true);

        instance.addMasteryPoint(player);
        instance.setCoolDown(COOLDOWN, mode);
    }
}
