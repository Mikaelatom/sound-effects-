package com.mikaelatom.tensuragacha.gacha;

import com.mikaelatom.tensuragacha.GachaConfig;
import com.mikaelatom.tensuragacha.registry.ModAttachments;
import com.mikaelatom.tensuragacha.registry.ModItems;
import com.mikaelatom.tensuragacha.registry.ModSkills;
import com.mikaelatom.tensuragacha.registry.ModSounds;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Soul Gacha: rolls a random Unique Skill the player doesn't own yet.
 * Outcomes, in order: jackpot (Gambler), Astral Core, then a random Unique Skill from the whole registry,
 * so Unique Skills from Tensura itself and from other add-ons are all in the pool.
 */
public final class GachaRoller {
    private GachaRoller() {
    }

    /** @return true if the roll happened and the ticket should be consumed. */
    public static boolean roll(ServerPlayer player) {
        Skills skills = SkillAPI.getSkillsFrom(player);
        ManasSkill gambler = ModSkills.GAMBLER.get();
        boolean ownsGambler = skills.getSkill(gambler.getRegistryName()).isPresent();

        int pity = player.getData(ModAttachments.PITY) + 1;
        int pityThreshold = GachaConfig.PITY_THRESHOLD.get();
        // Luck (which Astral Slimes have plenty of) boosts the odds.
        double luck = Math.max(0, player.getAttributeValue(Attributes.LUCK));
        double jackpotChance = GachaConfig.JACKPOT_CHANCE.get() * (1 + luck * 0.1);

        if (!ownsGambler && ((pityThreshold > 0 && pity >= pityThreshold) || player.getRandom().nextDouble() < jackpotChance)) {
            player.setData(ModAttachments.PITY, 0);
            if (skills.learnSkill(gambler.createDefaultInstance(), learnedMessage(gambler))) {
                announceJackpot(player);
                return true;
            }
        }

        if (player.getRandom().nextDouble() < GachaConfig.ASTRAL_CORE_CHANCE.get()) {
            player.setData(ModAttachments.PITY, pity);
            ItemStack core = new ItemStack(ModItems.ASTRAL_CORE.get());
            if (!player.getInventory().add(core)) player.drop(core, false);
            player.sendSystemMessage(Component.translatable("tensuragacha.gacha.astral_core").withStyle(ChatFormatting.LIGHT_PURPLE));
            playPop(player, 1.4F);
            return true;
        }

        List<ManasSkill> pool = buildPool(skills);
        if (pool.isEmpty()) {
            player.displayClientMessage(Component.translatable("tensuragacha.gacha.pool_empty").withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        ManasSkill rolled = pool.get(player.getRandom().nextInt(pool.size()));
        if (!skills.learnSkill(rolled.createDefaultInstance(), learnedMessage(rolled))) {
            player.displayClientMessage(Component.translatable("tensuragacha.gacha.failed").withStyle(ChatFormatting.RED), true);
            return false;
        }

        player.setData(ModAttachments.PITY, pity);
        playPop(player, 1.0F);
        if (pityThreshold > 0 && !ownsGambler) {
            player.displayClientMessage(Component.translatable("tensuragacha.gacha.pity", pity, pityThreshold)
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private static List<ManasSkill> buildPool(Skills skills) {
        List<? extends String> blacklist = GachaConfig.BLACKLIST.get();
        List<ManasSkill> pool = new ArrayList<>();
        for (ManasSkill skill : SkillAPI.getSkillRegistry()) {
            if (!(skill instanceof Skill tensuraSkill) || tensuraSkill.getType() != Skill.SkillType.UNIQUE) continue;
            if (skill == ModSkills.GAMBLER.get()) continue; // jackpot only
            ResourceLocation id = skill.getRegistryName();
            if (id == null || blacklist.contains(id.toString())) continue;
            if (skills.getSkill(id).isPresent()) continue;
            pool.add(skill);
        }
        return pool;
    }

    private static net.minecraft.network.chat.MutableComponent learnedMessage(ManasSkill skill) {
        return Component.translatable("tensuragacha.gacha.learned", skill.getName()).withStyle(ChatFormatting.GOLD);
    }

    private static void announceJackpot(ServerPlayer player) {
        Component message = Component.translatable("tensuragacha.gacha.jackpot", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        player.server.getPlayerList().broadcastSystemMessage(message, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        playPop(player, 0.7F);
    }

    private static void playPop(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(), ModSounds.SQUISH_POP.get(), SoundSource.PLAYERS, 1.0F, pitch);
    }
}
