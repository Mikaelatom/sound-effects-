package com.tensurafragments.card;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ally.Allies;
import com.tensurafragments.skill.GambitCards;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.MomentumTracker;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Spell Cards: Gambit Cards' Inscribe mode turns magicules into cards carrying a spell. Thrown, they stick like any
 * Gambit card (and can be teleported to); detonated, they cast their spell. Several on one target go off as a chain,
 * each link stronger than the last, and some mixes finish with a combo.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class SpellCards {
    /** Ticks between the links of a chain. */
    private static final int LINK_TICKS = 6;
    /** Spell Cards stuck to blocks within this distance of each other count as one target. */
    private static final double SAME_SPOT = 1.75;
    private static final List<Pending> PENDING = new ArrayList<>();

    /** Something to cast later, at a target that may have moved. */
    private record Pending(long runAt, ResourceKey<Level> level, UUID owner, WeakReference<Entity> anchor, Vec3 pos,
                           BiConsumer<ServerPlayer, Vec3> action) {
    }

    /** The finishers a chain can end in. */
    public enum Combo {
        FIRE_TORNADO, THUNDERSTORM, STEAM_EXPLOSION, CATACLYSM, THREE_OF_A_KIND;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private SpellCards() {
    }

    // ---- Making and throwing ----

    public static SpellCard selected(ServerPlayer player) {
        SpellCard spell = SpellCard.byIndex(player.getData(ModRegistries.SELECTED_SPELL_CARD));
        return spell == null ? SpellCard.FLAME : spell;
    }

    public static int cost(SpellCard spell) {
        return (int) Math.round(spell.cost() * Config.SPELL_CARD_COST_MULTIPLIER.get());
    }

    public static void cycle(ServerPlayer player) {
        SpellCard spell = selected(player).next();
        player.setData(ModRegistries.SELECTED_SPELL_CARD, spell.ordinal());
        player.displayClientMessage(Component.translatable("tensurafragments.spell_card.selected",
                Component.translatable("item.tensurafragments." + spell.id() + "_card"), cost(spell))
                .withColor(spell.colour()), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.8F, 1.3F);
        GambitCards.sync(player);
    }

    /** Inscribe the selected spell into a Spell Card, paid in magicules. */
    public static boolean inscribe(ServerPlayer player) {
        SpellCard spell = selected(player);
        int cost = cost(spell);
        if (!Magicules.trySpend(player, cost)) {
            player.displayClientMessage(Component.translatable("tensurafragments.spell_card.no_magicules", cost), true);
            return false;
        }
        ItemStack card = new ItemStack(ModRegistries.SPELL_CARD_ITEMS.get(spell).get());
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.4, 0.4, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    public static CardEntity throwCard(ServerPlayer player, SpellCard spell) {
        List<CardEntity> spells = new ArrayList<>(GambitCards.getCards(player).stream().filter(CardEntity::isSpellCard).toList());
        int overflow = spells.size() - Config.MAX_SPELL_CARDS.get() + 1;
        for (int i = 0; i < overflow; i++) {
            spells.get(i).fizzle(); // oldest first
        }
        CardEntity card = CardEntity.createSpell(player, spell);
        Vec3 velocity = player.getLookAngle().scale(Config.THROW_SPEED.get())
                .add(GambitCards.clampSpeed(MomentumTracker.velocity(player)));
        card.setDeltaMovement(velocity);
        player.level().addFreshEntity(card);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.6F, 1.5F);
        return card;
    }

    // ---- Detonating: chains and combos ----

    /** A Spell Card went off: it and every other Spell Card of yours on the same target go off as a chain. */
    public static void detonate(CardEntity trigger) {
        if (trigger.hasExploded() || !(trigger.level() instanceof ServerLevel level)
                || !(trigger.getOwner() instanceof ServerPlayer owner)) {
            trigger.fizzle();
            return;
        }
        List<CardEntity> chain = linked(trigger, owner);
        Entity anchor = trigger.getStuckEntity();
        Vec3 pos = trigger.position();
        List<SpellCard> spells = new ArrayList<>();
        for (CardEntity card : chain) {
            spells.add(card.getSpell());
            card.spend();
        }
        long now = level.getGameTime();
        float base = Config.SPELL_CARD_POWER.get().floatValue();
        for (int i = 0; i < spells.size(); i++) {
            SpellCard spell = spells.get(i);
            float power = base * (1 + 0.5F * i);
            int link = i + 1;
            schedule(now + (long) i * LINK_TICKS, level, owner, anchor, pos, (caster, at) -> {
                if (spells.size() > 1) {
                    caster.displayClientMessage(Component.translatable("tensurafragments.spell_card.chain", link,
                            Math.round(100 * (1 + 0.5F * (link - 1)))).withColor(spell.colour()), true);
                }
                cast(spell, caster, at, power, anchor);
            });
        }
        Combo combo = combo(spells);
        if (combo != null) {
            float power = base * (1 + 0.25F * (spells.size() - 2));
            SpellCard most = mostCommon(spells);
            schedule(now + (long) spells.size() * LINK_TICKS, level, owner, anchor, pos, (caster, at) -> {
                caster.displayClientMessage(Component.translatable("tensurafragments.spell_card.combo",
                        Component.translatable("tensurafragments.spell_card.combo." + combo.id())).withColor(0xFFD34D), true);
                cast(combo, most, caster, at, power);
            });
        }
    }

    /** The trigger and your other live Spell Cards on the same target (the same creature, or the same spot), oldest first. */
    private static List<CardEntity> linked(CardEntity trigger, ServerPlayer owner) {
        Entity anchor = trigger.getStuckEntity();
        List<CardEntity> chain = new ArrayList<>();
        for (CardEntity card : GambitCards.getCards(owner)) {
            if (!card.isSpellCard() || card.hasExploded()) {
                continue;
            }
            Entity other = card.getStuckEntity();
            boolean same = anchor != null ? other == anchor
                    : other == null && card.position().distanceTo(trigger.position()) <= SAME_SPOT;
            if (same || card == trigger) {
                chain.add(card);
            }
        }
        chain.sort(Comparator.comparingInt((CardEntity card) -> card.tickCount).reversed());
        return chain;
    }

    /** Which finisher, if any, a chain's spells make. */
    @Nullable
    public static Combo combo(List<SpellCard> spells) {
        if (spells.size() < 2) {
            return null;
        }
        if (spells.contains(SpellCard.QUAKE) && spells.contains(SpellCard.METEOR)) {
            return Combo.CATACLYSM;
        }
        if (spells.contains(SpellCard.THUNDER) && spells.contains(SpellCard.GALE)) {
            return Combo.THUNDERSTORM;
        }
        if (spells.contains(SpellCard.FLAME) && spells.contains(SpellCard.GALE)) {
            return Combo.FIRE_TORNADO;
        }
        if (spells.contains(SpellCard.FLAME) && spells.contains(SpellCard.FROST)) {
            return Combo.STEAM_EXPLOSION;
        }
        return count(spells, mostCommon(spells)) >= 3 ? Combo.THREE_OF_A_KIND : null;
    }

    private static SpellCard mostCommon(List<SpellCard> spells) {
        Map<SpellCard, Integer> counts = new EnumMap<>(SpellCard.class);
        for (SpellCard spell : spells) {
            counts.merge(spell, 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(SpellCard.FLAME);
    }

    private static int count(List<SpellCard> spells, SpellCard spell) {
        return (int) spells.stream().filter(s -> s == spell).count();
    }

    private static void schedule(long runAt, ServerLevel level, ServerPlayer owner, @Nullable Entity anchor, Vec3 pos,
                                 BiConsumer<ServerPlayer, Vec3> action) {
        Pending pending = new Pending(runAt, level.dimension(), owner.getUUID(), new WeakReference<>(anchor), pos, action);
        if (runAt <= level.getGameTime()) {
            run(level.getServer(), pending);
        } else {
            PENDING.add(pending);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        List<Pending> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.runAt() <= now) {
                due.add(pending);
                it.remove();
            }
        }
        due.forEach(pending -> run(server, pending));
    }

    private static void run(MinecraftServer server, Pending pending) {
        ServerLevel level = server.getLevel(pending.level());
        if (level == null || !(level.getPlayerByUUID(pending.owner()) instanceof ServerPlayer owner)) {
            return;
        }
        Entity anchor = pending.anchor().get();
        Vec3 at = anchor != null && anchor.isAlive() && anchor.level() == level
                ? anchor.getBoundingBox().getCenter() : pending.pos();
        pending.action().accept(owner, at);
    }

    // ---- The spells ----

    /** Radius grows a little with power (a quarter per extra 100%). */
    private static double radius(double base, float power) {
        return base * (0.75 + 0.25 * power);
    }

    public static void cast(SpellCard spell, ServerPlayer owner, Vec3 at, float power, @Nullable Entity anchor) {
        ServerLevel level = owner.serverLevel();
        switch (spell) {
            case FLAME -> {
                double r = radius(4, power);
                for (LivingEntity target : enemies(level, owner, at, r)) {
                    hurt(owner, target, 20 * power * falloff(target, at, r));
                    target.igniteForSeconds(8);
                }
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, (int) (60 * r / 4), r / 2.5, r / 3, r / 2.5, 0.08);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 12, r / 3, 0.3, r / 3, 0);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.7F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1F, 1.3F);
            }
            case FROST -> {
                double r = radius(4, power);
                for (LivingEntity target : enemies(level, owner, at, r)) {
                    hurt(owner, target, 14 * power * falloff(target, at, r));
                    target.clearFire();
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3), owner);
                    target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 120, 1), owner);
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), 300));
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, (int) (70 * r / 4), r / 2.5, r / 3, r / 2.5, 0.06);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 25, r / 3, 0.4, r / 3, 0.1);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 2F, 0.6F);
            }
            case THUNDER -> {
                LivingEntity main = anchor instanceof LivingEntity living && living.isAlive() && !Allies.isFriendly(living, owner)
                        ? living : enemies(level, owner, at, 2.5).stream().findFirst().orElse(null);
                bolt(level, main != null ? main.position() : at.subtract(0, 1, 0));
                if (main != null) {
                    hurt(owner, main, 28 * power);
                }
                int jumps = 0;
                for (LivingEntity target : enemies(level, owner, at, radius(8, power))) {
                    if (target == main || jumps >= 4) {
                        continue;
                    }
                    jumps++;
                    bolt(level, target.position());
                    hurt(owner, target, 14 * power);
                }
            }
            case GALE -> CardTornadoEntity.spawn(owner, CardTornadoEntity.Variant.WIND, at, Vec3.ZERO,
                    radius(2.5, power), 100, 4 * power, radius(8, power));
            case QUAKE -> {
                double r = radius(8, power);
                for (LivingEntity target : enemies(level, owner, at, r)) {
                    hurt(owner, target, 18 * power * falloff(target, at, r));
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 0.9 * Math.min(power, 2), 0));
                    target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), owner);
                }
                BlockState ground = level.getBlockState(net.minecraft.core.BlockPos.containing(at).below());
                if (!ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y, at.z,
                            (int) (40 * r / 4), r / 2, 0.2, r / 2, 0.3);
                }
                ring(level, at, r);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F, 0.5F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
            }
            case METEOR -> meteor(owner, at, radius(10, power), 50 * power, 20);
        }
    }

    /** A combo's finisher. {@code most} is the chain's most common spell (for Three of a Kind). */
    public static void cast(Combo combo, SpellCard most, ServerPlayer owner, Vec3 at, float power) {
        ServerLevel level = owner.serverLevel();
        switch (combo) {
            case FIRE_TORNADO -> {
                Vec3 away = at.subtract(owner.position()).multiply(1, 0, 1);
                Vec3 heading = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, owner.getYRot()) : away.normalize();
                CardTornadoEntity.spawn(owner, CardTornadoEntity.Variant.FIRE, at, heading.scale(0.18),
                        radius(3.5, power), 160, 8 * power, radius(9, power));
            }
            case THUNDERSTORM -> CardTornadoEntity.spawn(owner, CardTornadoEntity.Variant.STORM, at, Vec3.ZERO,
                    radius(4, power), 140, 6 * power, radius(11, power));
            case STEAM_EXPLOSION -> {
                double r = radius(9, power);
                for (LivingEntity target : enemies(level, owner, at, r)) {
                    hurt(owner, target, 30 * power * falloff(target, at, r));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), owner);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2), owner);
                }
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 300, r / 2, r / 3, r / 2, 0.15);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                ring(level, at, r);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 3F, 0.5F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2F, 0.7F);
            }
            case CATACLYSM -> meteor(owner, at, radius(16, power), 80 * power, 30);
            case THREE_OF_A_KIND -> cast(most, owner, at, power * 2, null);
        }
    }

    /** A meteor streaks down from the sky and lands after {@code delay} ticks: a huge blast that sets everything alight. */
    private static void meteor(ServerPlayer owner, Vec3 at, double radius, float damage, int delay) {
        ServerLevel level = owner.serverLevel();
        Vec3 sky = at.add(-8, 24, -8);
        for (int i = 0; i <= 20; i++) {
            Vec3 p = sky.lerp(at, i / 20.0);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 2, 0.3, 0.3, 0.3, 0.01);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2F, 0.4F);
        schedule(level.getGameTime() + delay, level, owner, null, at, (caster, where) -> {
            ServerLevel l = caster.serverLevel();
            for (LivingEntity target : enemies(l, caster, where, radius)) {
                hurt(caster, target, damage * falloff(target, where, radius));
                target.igniteForSeconds(6);
                Vec3 push = target.position().subtract(where).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(1.2);
                target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.7, push.z));
                target.hurtMarked = true;
            }
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, where.x, where.y, where.z, (int) Math.max(1, radius / 5),
                    radius / 4, 0.5, radius / 4, 0);
            l.sendParticles(ParticleTypes.FLAME, where.x, where.y, where.z, (int) (radius * 25), radius / 2, radius / 4,
                    radius / 2, 0.15);
            l.sendParticles(ParticleTypes.LAVA, where.x, where.y, where.z, (int) (radius * 3), radius / 3, 0.5, radius / 3, 0);
            ring(l, where, radius);
            l.playSound(null, where.x, where.y, where.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4F, 0.5F);
            l.playSound(null, where.x, where.y, where.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 2F, 0.6F);
        });
    }

    // ---- Shared ----

    /** Living things in range that aren't you or yours. */
    static List<LivingEntity> enemies(ServerLevel level, ServerPlayer owner, Vec3 at, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, owner)
                        && e.getBoundingBox().getCenter().distanceTo(at) <= radius + e.getBbWidth() / 2);
    }

    /** Full damage in the inner half of the radius, falling to half at the edge. */
    private static float falloff(LivingEntity target, Vec3 at, double radius) {
        double d = target.getBoundingBox().getCenter().distanceTo(at) / Math.max(radius, 0.01);
        return (float) (d <= 0.5 ? 1 : 1 - (d - 0.5));
    }

    static void hurt(ServerPlayer owner, LivingEntity target, float damage) {
        if (damage <= 0) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
    }

    /** A real-looking lightning strike that hurts nothing by itself (the spell does the damage). */
    static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private static void ring(ServerLevel level, Vec3 at, double radius) {
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.9F, (float) radius),
                at.x, at.y, at.z);
    }
}
