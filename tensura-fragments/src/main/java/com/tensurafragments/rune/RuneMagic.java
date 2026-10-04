package com.tensurafragments.rune;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.ally.Allies;
import com.tensurafragments.network.OpenRuneCanvasPayload;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Rune Magic: draw a rune onto paper (with the skill: hold paper, press the skill key, draw on the dots), then use
 * the rune paper on a creature (enemies get the harmful side, you and your allies the helpful one) or inscribe it on a
 * weapon, whose hits carry the rune until its charges run out.
 */
public final class RuneMagic {
    private RuneMagic() {
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.RUNE_MAGIC.getId()).isPresent();
    }

    /** Gives the skill (and with it the codex). */
    public static void grantSkill(ServerPlayer player) {
        if (!hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.RUNE_MAGIC.get());
        }
        giveCodex(player);
    }

    /** Every Rune Magic user gets one Rune Codex (once; a lost one can be crafted again). */
    public static void giveCodex(ServerPlayer player) {
        if (player.getData(ModRegistries.RUNE_CODEX_GIVEN) || !hasSkill(player)) {
            return;
        }
        player.setData(ModRegistries.RUNE_CODEX_GIVEN, true);
        ItemStack codex = new ItemStack(ModRegistries.RUNE_CODEX.get());
        if (!player.getInventory().add(codex)) {
            player.drop(codex, false);
        }
    }

    // ---- Drawing ----

    /** The skill key: with paper on you, the drawing sheet opens. */
    public static boolean startDrawing(ServerPlayer player) {
        if (!player.getInventory().hasAnyOf(java.util.Set.of(Items.PAPER))) {
            player.displayClientMessage(Component.translatable("tensurafragments.rune.need_paper"), true);
            return false;
        }
        PacketDistributor.sendToPlayer(player, new OpenRuneCanvasPayload());
        return true;
    }

    /** What the player drew. Returns the rune made, or null if the lines aren't a rune (the paper is kept then). */
    public static Rune finishDrawing(ServerPlayer player, Collection<Integer> segments) {
        if (!hasSkill(player)) {
            return null;
        }
        Rune rune = Rune.match(segments);
        if (rune == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.rune.no_match").withStyle(ChatFormatting.RED),
                    true);
            player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.2F);
            return null;
        }
        int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.PAPER));
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("tensurafragments.rune.need_paper"), true);
            return null;
        }
        if (!player.getAbilities().instabuild && !Magicules.trySpend(player, Config.RUNE_DRAW_MAGICULES.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return null;
        }
        if (!player.getAbilities().instabuild) {
            player.getInventory().getItem(slot).shrink(1);
        }
        ItemStack paper = RunePaperItem.of(rune);
        if (!player.getInventory().add(paper)) {
            player.drop(paper, false);
        }
        player.displayClientMessage(Component.translatable("tensurafragments.rune.drawn",
                Component.translatable(rune.translationKey())).withColor(rune.colour()), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        SkillAPI.getSkillsFrom(player).getSkill(ModSkills.RUNE_MAGIC.getId())
                .ifPresent(instance -> instance.getSkill().addMasteryPoint(instance, player));
        return rune;
    }

    // ---- On a creature ----

    private static float power() {
        return Config.RUNE_POWER.get().floatValue();
    }

    private static int ticks(int seconds) {
        return Math.round(seconds * 20 * power());
    }

    private static void effect(LivingEntity target, Holder<MobEffect> effect, int seconds, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks(seconds), amplifier));
    }

    /** Uses a rune on a creature: the harmful side on an enemy, the helpful side on you or a friend. */
    public static void useOn(ServerPlayer player, LivingEntity target, Rune rune) {
        boolean friend = target == player || Allies.isFriendly(target, player);
        ServerLevel level = player.serverLevel();
        float damage = 6 * power();
        switch (rune) {
            case FIRE -> {
                if (friend) {
                    effect(target, MobEffects.FIRE_RESISTANCE, 180, 0);
                } else {
                    target.igniteForSeconds(8 * power());
                    target.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
            }
            case FROST -> {
                if (friend) {
                    target.clearFire();
                    effect(target, MobEffects.DAMAGE_RESISTANCE, 60, 0);
                } else {
                    effect(target, MobEffects.MOVEMENT_SLOWDOWN, 6, 3);
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + ticks(6)));
                    target.hurt(player.damageSources().freeze(), damage * 0.7F);
                }
            }
            case THUNDER -> {
                if (friend) {
                    effect(target, MobEffects.MOVEMENT_SPEED, 60, 1);
                    effect(target, MobEffects.DIG_SPEED, 60, 1);
                } else {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(target.position());
                        bolt.setCause(player);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    target.hurt(player.damageSources().indirectMagic(player, player), damage * 1.4F);
                }
            }
            case LIFE -> {
                target.heal(8 * power());
                effect(target, MobEffects.REGENERATION, 10, 1);
            }
            case GUARD -> {
                if (friend) {
                    effect(target, MobEffects.ABSORPTION, 60, 1);
                    effect(target, MobEffects.DAMAGE_RESISTANCE, 30, 0);
                } else {
                    effect(target, MobEffects.WEAKNESS, 20, 1);
                }
            }
            case WIND -> {
                if (friend) {
                    effect(target, MobEffects.MOVEMENT_SPEED, 60, 1);
                    effect(target, MobEffects.JUMP, 60, 1);
                    effect(target, MobEffects.SLOW_FALLING, 60, 0);
                } else {
                    Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(1.2 * power());
                    target.setDeltaMovement(away.x, 1.3 * power(), away.z);
                    target.hurtMarked = true;
                }
            }
            case BIND -> {
                if (friend) {
                    target.getActiveEffects().stream().filter(e -> !e.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect)
                            .toList().forEach(target::removeEffect);
                } else {
                    effect(target, MobEffects.MOVEMENT_SLOWDOWN, 5, 9);
                    effect(target, MobEffects.JUMP, 5, 128);
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                }
            }
            case SIGHT -> {
                if (friend) {
                    effect(target, MobEffects.NIGHT_VISION, 180, 0);
                } else {
                    effect(target, MobEffects.GLOWING, 60, 0);
                }
            }
        }
        burst(level, target, rune, 24);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.2F, 1.1F);
    }

    static void burst(ServerLevel level, LivingEntity target, Rune rune, int count) {
        int c = rune.colour();
        level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F,
                        (c & 0xFF) / 255F), 1.2F), target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                count, target.getBbWidth() / 2 + 0.2, target.getBbHeight() / 2, target.getBbWidth() / 2 + 0.2, 0.02);
    }

    // ---- On a weapon ----

    /** Weapons and tools (anything that wears down) can carry a rune. */
    public static boolean canInscribe(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && !(stack.getItem() instanceof RunePaperItem);
    }

    public static void inscribe(ItemStack weapon, Rune rune) {
        weapon.set(ModRegistries.WEAPON_RUNE.get(), new WeaponRune(rune.id(), Config.RUNE_WEAPON_CHARGES.get()));
    }

    /** A hit with a rune weapon: the rune's effect, one charge used. */
    public static void onWeaponHit(LivingEntity attacker, LivingEntity target, ItemStack weapon, float damage) {
        WeaponRune inscribed = weapon.get(ModRegistries.WEAPON_RUNE.get());
        Rune rune = inscribed == null ? null : inscribed.rune();
        if (rune == null || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        float power = power();
        switch (rune) {
            case FIRE -> {
                target.igniteForSeconds(4 * power);
                target.invulnerableTime = 0;
                target.hurt(attacker.damageSources().onFire(), 2 * power);
            }
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks(3), 1));
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + ticks(2)));
            }
            case THUNDER -> {
                if (inscribed.charges() % 4 == 0) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(target.position());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    target.invulnerableTime = 0;
                    target.hurt(attacker.damageSources().lightningBolt(), 6 * power);
                }
            }
            case LIFE -> attacker.heal(damage * 0.25F * power);
            case GUARD -> attacker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks(5), 0));
            case WIND -> {
                Vec3 away = target.position().subtract(attacker.position()).multiply(1, 0, 1);
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(1.1 * power);
                    target.push(away.x, 0.35, away.z);
                    target.hurtMarked = true;
                }
            }
            case BIND -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks(2), 9));
            case SIGHT -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks(10), 0));
        }
        burst(level, target, rune, 8);
        int left = inscribed.charges() - 1;
        if (left <= 0) {
            weapon.remove(ModRegistries.WEAPON_RUNE.get());
            if (attacker instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("tensurafragments.rune.faded",
                        Component.translatable(rune.translationKey())), true);
            }
        } else {
            weapon.set(ModRegistries.WEAPON_RUNE.get(), new WeaponRune(inscribed.id(), left));
        }
    }
}
