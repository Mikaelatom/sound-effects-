package com.tensurafragments.magic;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Learning Tensura's magic by surviving it: every time a Tensura spell you don't know hits you and you live, you
 * get closer to understanding it. Survive it enough times and you learn it (through Tensura's own learning, so its
 * usual rules and messages apply).
 */
public final class SpellLearning {
    private SpellLearning() {
    }

    /** Called after a player took damage (and is still alive). */
    public static void onSurvivedHit(ServerPlayer player, DamageSource source) {
        int needed = Config.SURVIVALS_TO_LEARN_SPELL.get();
        if (needed <= 0 || !player.isAlive() || source.getEntity() == player) {
            return;
        }
        Magic magic = spellBehind(source);
        if (magic == null) {
            return;
        }
        ResourceLocation id = magic.getRegistryName();
        if (id == null || SkillAPI.getSkillsFrom(player).getSkill(magic).isPresent()) {
            return;
        }
        Map<String, Integer> exposure = new HashMap<>(player.getData(ModRegistries.SPELL_EXPOSURE));
        int survived = exposure.getOrDefault(id.toString(), 0) + 1;
        Component name = magic.getName();
        if (survived >= needed) {
            exposure.remove(id.toString());
            player.setData(ModRegistries.SPELL_EXPOSURE, exposure);
            if (SkillHelper.learnSkill(player, magic)) {
                player.displayClientMessage(Component.translatable("tensurafragments.magic.learned_by_surviving", name), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            return;
        }
        exposure.put(id.toString(), survived);
        player.setData(ModRegistries.SPELL_EXPOSURE, exposure);
        player.displayClientMessage(Component.translatable("tensurafragments.magic.surviving", name, survived, needed), true);
    }

    /** How many times the player has survived {@code magic} so far (toward learning it). */
    public static int survived(ServerPlayer player, ManasSkill magic) {
        ResourceLocation id = magic.getRegistryName();
        return id == null ? 0 : player.getData(ModRegistries.SPELL_EXPOSURE).getOrDefault(id.toString(), 0);
    }

    /**
     * The Tensura spell behind a hit: the ability the damage came from, else the spell its projectile was cast with,
     * else the spell named like its projectile (a fire_ball projectile is the Fire Ball spell).
     */
    @Nullable
    public static Magic spellBehind(DamageSource source) {
        if (source instanceof TensuraDamageSource tensura) {
            ManasSkillInstance instance = tensura.tensura$getAbilityInstance();
            if (instance != null && instance.getSkill() instanceof Magic magic) {
                return magic;
            }
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof TensuraProjectile projectile) {
            ManasSkillInstance instance = projectile.getSkill();
            if (instance != null && instance.getSkill() instanceof Magic magic) {
                return magic;
            }
        }
        if (direct != null) {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
            ManasSkill named = SkillAPI.getSkillRegistry().get(type);
            if (named instanceof Magic magic) {
                return magic;
            }
        }
        return null;
    }
}
