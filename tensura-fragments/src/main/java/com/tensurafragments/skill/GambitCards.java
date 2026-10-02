package com.tensurafragments.skill;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.card.CardEntity;
import com.tensurafragments.card.SpellCards;
import com.tensurafragments.network.SyncDeckPayload;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gambit Cards, the first skill. Triggered through {@link GambitCardsSkill}; magicule costs are charged by Tensura.
 * <ul>
 *   <li>Throw: a card flies where you aim and sticks to whatever it hits. It inherits your momentum.</li>
 *   <li>Teleport: warp to the card under your crosshair (or your newest one). Your speed is kept and
 *       redirected where you look, so chained teleports build momentum.</li>
 *   <li>Detonate: blow up every card (or, while sneaking, only the aimed one). Blasts chain into nearby cards
 *       and hurt you too if you are too close.</li>
 * </ul>
 * Cards come from a small regenerating deck, cost magicules, charge up the longer they sit and fizzle after a
 * time limit.
 */
public final class GambitCards {
    private static final double AIM_CONE_COS = Math.cos(Math.toRadians(12));
    private static final double MAX_TARGET_DISTANCE = 96;

    private GambitCards() {
    }

    public static void throwCard(ServerPlayer player) {
        int deck = getDeck(player);
        if (deck <= 0) {
            player.displayClientMessage(Component.translatable("tensurafragments.cards.deck_empty"), true);
            return;
        }

        List<CardEntity> active = new ArrayList<>(getCards(player).stream().filter(card -> !card.isSpellCard()).toList());
        int overflow = active.size() - Config.MAX_ACTIVE_CARDS.get() + 1;
        for (int i = 0; i < overflow; i++) {
            active.get(i).fizzle(); // oldest first
        }

        CardEntity card = CardEntity.create(player);
        Vec3 velocity = player.getLookAngle().scale(Config.THROW_SPEED.get()).add(clampSpeed(MomentumTracker.velocity(player)));
        card.setDeltaMovement(velocity);
        player.level().addFreshEntity(card);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.8F);

        setDeck(player, deck - 1);
    }

    public static void teleport(ServerPlayer player) {
        CardEntity card = findTarget(player, false);
        if (card == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.cards.no_card"), true);
            return;
        }

        Vec3 destination = teleportDestination(player, card);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.2);

        // Momentum: keep your speed but send it where you're looking.
        double speed = clampSpeed(MomentumTracker.velocity(player)).length() * Config.MOMENTUM_CARRY.get();
        Vec3 carried = player.getLookAngle().scale(speed);

        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();
        player.setDeltaMovement(carried);
        player.hurtMarked = true;
        MomentumTracker.reset(player, carried);
        card.discard();

        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.6F, 2.0F),
                destination.x, destination.y + player.getBbHeight() / 2, destination.z);
        level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    public static void detonate(ServerPlayer player, boolean onlyAimed) {
        if (onlyAimed) {
            CardEntity card = findTarget(player, true);
            if (card != null) {
                card.prime(1);
            }
            return;
        }
        for (CardEntity card : getCards(player)) {
            card.prime(1);
        }
    }

    /** Gives the player the skill if they don't have it yet (the originals are stripped, so this is the kit). */
    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_GAMBIT_CARDS.get()
                && SkillAPI.getSkillsFrom(player).getSkill(ModSkills.GAMBIT_CARDS.getId()).isEmpty()) {
            SkillHelper.learnSkill(player, ModSkills.GAMBIT_CARDS.get());
        }
    }

    /** Called every server tick for every player. */
    public static void tickDeck(ServerPlayer player) {
        int max = Config.DECK_SIZE.get();
        int deck = getDeck(player);
        if (deck >= max) {
            player.setData(ModRegistries.DECK_REGEN, 0);
            return;
        }
        int regen = player.getData(ModRegistries.DECK_REGEN) + 1;
        if (regen >= Config.DECK_REGEN_TICKS.get()) {
            player.setData(ModRegistries.DECK_REGEN, 0);
            setDeck(player, deck + 1);
        } else {
            player.setData(ModRegistries.DECK_REGEN, regen);
        }
    }

    public static int getDeck(ServerPlayer player) {
        int deck = player.getData(ModRegistries.DECK);
        int max = Config.DECK_SIZE.get();
        return deck < 0 ? max : Math.min(deck, max);
    }

    public static void setDeck(ServerPlayer player, int deck) {
        player.setData(ModRegistries.DECK, deck);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncDeckPayload(getDeck(player), Config.DECK_SIZE.get(),
                Config.DECK_REGEN_TICKS.get(), player.getData(ModRegistries.DECK_REGEN),
                SpellCards.selected(player).ordinal()));
    }

    /** The player's live cards in their current dimension, oldest first. */
    public static List<CardEntity> getCards(ServerPlayer player) {
        List<CardEntity> cards = new ArrayList<>(player.serverLevel().getEntities(ModRegistries.CARD.get(),
                card -> card.isAlive() && card.getOwner() == player));
        cards.sort(Comparator.comparingInt((CardEntity card) -> card.tickCount).reversed());
        return cards;
    }

    /** The card closest to the crosshair, or the newest card if none is aimed at and {@code aimedOnly} is false. */
    private static CardEntity findTarget(ServerPlayer player, boolean aimedOnly) {
        List<CardEntity> cards = getCards(player);
        if (cards.isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        CardEntity best = null;
        double bestDot = AIM_CONE_COS;
        for (CardEntity card : cards) {
            Vec3 toCard = card.position().subtract(eye);
            double distance = toCard.length();
            if (distance > MAX_TARGET_DISTANCE || distance < 1.0E-3) {
                continue;
            }
            double dot = toCard.scale(1 / distance).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = card;
            }
        }
        if (best == null && !aimedOnly) {
            best = cards.get(cards.size() - 1);
        }
        return best;
    }

    private static Vec3 teleportDestination(ServerPlayer player, CardEntity card) {
        Vec3 pos = card.position();
        if (card.isStuck() && !card.isStuckToEntity()) {
            Direction face = card.getFace();
            pos = pos.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.4));
            if (face == Direction.DOWN) {
                pos = pos.subtract(0, player.getBbHeight(), 0);
            } else if (face != Direction.UP) {
                pos = pos.subtract(0, player.getBbHeight() / 2, 0);
            }
        }
        // Nudge upwards out of blocks if the spot is cramped.
        for (int i = 0; i < 3; i++) {
            Vec3 offset = pos.subtract(player.position());
            if (player.level().noCollision(player, player.getBoundingBox().move(offset))) {
                break;
            }
            pos = pos.add(0, 0.5, 0);
        }
        return pos;
    }

    public static Vec3 clampSpeed(Vec3 velocity) {
        double max = Config.MAX_CARRIED_SPEED.get();
        double length = velocity.length();
        if (!Double.isFinite(length)) {
            return Vec3.ZERO;
        }
        return length > max ? velocity.scale(max / length) : velocity;
    }
}
