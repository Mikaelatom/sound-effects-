package com.tensurafragments.combat;

import com.tensurafragments.ally.Allies;
import com.tensurafragments.combatanim.CombatAnimations;
import com.tensurafragments.skill.EpScaling;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Explosion skill's moves (a hero who fights with explosions from his palms): the Explosion fighting style in
 * Combat Mode, and the skill key's long-range blasts. Explosions here hurt and throw creatures but break no blocks.
 *
 * <p>Each move's blast goes off at its animation's "strike" time (from the animation file), so it lands with the palm.
 * Until an animation exists, a similar one from the other styles plays instead and a default time is used.
 */
public final class ExplosionMoves {
    static final String IDLE = "explosion_idle";
    static final String BLAST_RIGHT = "explosion_blast_right";
    static final String BLAST_LEFT = "explosion_blast_left";
    static final String FINISHER = "explosion_finisher";
    static final String RISING = "explosion_rising_blast";
    static final String DIVE = "explosion_dive";
    static final String DIVE_LAND = "explosion_dive_land";
    static final String GRAB = "explosion_grab";
    static final String HOLD = "explosion_hold";
    static final String DETONATE = "explosion_grab_blast";
    static final String BURST_DASH = "explosion_burst_dash";
    static final String HOVER = "explosion_hover";
    static final String AP_SHOT = "explosion_ap_shot";
    static final String STUN_GRENADE = "explosion_stun_grenade";
    static final String HOWITZER = "explosion_howitzer";

    /** What plays until each animation is made: a similar one from the other styles. */
    private static final Map<String, String> STAND_INS = Map.ofEntries(
            Map.entry(IDLE, "idle_guard"), Map.entry(BLAST_RIGHT, "punch_hook_right"),
            Map.entry(BLAST_LEFT, "punch_hook_left"), Map.entry(FINISHER, "brawler_finisher"),
            Map.entry(RISING, "punch_uppercut_left"), Map.entry(DIVE, "brawler_slam_dive"),
            Map.entry(DIVE_LAND, "brawler_slam_land"), Map.entry(GRAB, "brawler_grab"), Map.entry(HOLD, "brawler_hold"),
            Map.entry(DETONATE, "brawler_throw"), Map.entry(BURST_DASH, "brawler_dash"), Map.entry(HOVER, "air_juggle"),
            Map.entry(AP_SHOT, "ki_palm"), Map.entry(STUN_GRENADE, "ki_burst"), Map.entry(HOWITZER, "swift_whirlwind"));

    /** Magicule costs. */
    static final double AP_SHOT_COST = 40;
    static final double STUN_GRENADE_COST = 60;
    static final double HOWITZER_COST = 250;
    static final double HOVER_COST = 1;

    /** Until when (game time) each player is hovering. */
    private static final Map<UUID, Long> HOVERING = new HashMap<>();

    private ExplosionMoves() {
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.EXPLOSION.getId()).isPresent();
    }

    static boolean isExplosion(ServerPlayer player) {
        return CombatMode.style(player) == FightingStyle.EXPLOSION;
    }

    /** The animation to play: the explosion one if it's been made, else its stand-in. */
    public static String anim(String name) {
        return CombatAnimations.get(name) != null ? name : STAND_INS.getOrDefault(name, name);
    }

    /** Ticks from a move's start to its blast: its animation's strike time, or {@code fallback} seconds. */
    static int strikeTicks(String name, float fallback) {
        CombatAnimations.Anim anim = CombatAnimations.get(name);
        float seconds = anim != null && anim.strikeSeconds() >= 0 ? anim.strikeSeconds() : fallback;
        return Math.round(seconds * 20);
    }

    // ---- The blast ----

    /**
     * An explosion at {@code at}: a flash, fire, smoke and a bang; it hurts and throws everything within
     * {@code radius} (not you, your allies or {@code spared}). Breaks no blocks. Returns how many it hit.
     */
    public static int blast(ServerPlayer player, Vec3 at, double radius, float damage, double knock, float down, int stun,
            @Nullable Entity spared) {
        ServerLevel level = player.serverLevel();
        effects(level, at, radius);
        // Combat Mode scales punches (and these) by EP itself; the skill key's blasts scale here.
        float amount = damage * (CombatMode.isOn(player) ? 1 : EpScaling.multiplier(player));
        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1),
                e -> e != player && e != spared && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player))) {
            Vec3 centre = target.position().add(0, target.getBbHeight() / 2, 0);
            if (centre.distanceTo(at) > radius + target.getBbWidth() / 2 + target.getBbHeight() / 2) {
                continue;
            }
            if (CombatMode.strike(player, target, amount, down, stun)) {
                hits++;
                throwFrom(target, at, knock);
            }
        }
        return hits;
    }

    /** The look of an explosion, with no harm done. */
    static void effects(ServerLevel level, Vec3 at, double radius) {
        if (radius >= 2.5) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, (int) Math.max(1, radius * 2), radius * 0.3,
                radius * 0.3, radius * 0.3, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, (int) (6 * radius), radius * 0.25, radius * 0.25,
                radius * 0.25, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, (int) (6 * radius), radius * 0.35, radius * 0.35,
                radius * 0.35, 0.04);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                (float) Math.min(2.0, 0.4 + radius * 0.3), (float) Math.max(0.7, 1.4 - radius * 0.12));
    }

    /** Thrown away from the blast (and a little up). */
    private static void throwFrom(LivingEntity target, Vec3 at, double knock) {
        if (knock <= 0) {
            return;
        }
        Vec3 away = target.position().add(0, target.getBbHeight() / 2, 0).subtract(at);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away.normalize();
        double resist = Math.max(0.3, 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        target.setDeltaMovement(away.x * knock * resist, Math.max(0.25, away.y * knock * 0.6 + 0.3) * resist,
                away.z * knock * resist);
        target.hurtMarked = true;
    }

    private static Vec3 chest(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
    }

    // ---- Combat Mode: the Explosion style ----

    /** Each punch that lands goes off in its face: a small blast that throws whatever's right behind it too. */
    static void punchPop(ServerPlayer player, LivingEntity target) {
        Vec3 at = chest(target);
        effects(player.serverLevel(), at, 1.0);
        blast(player, at, 1.6, CombatMode.punchDamage(player) * 0.4F, 0, 4, 0, target);
    }

    /** The finisher: both palms point-blank, a big blast that sends it flying. */
    static void finisher(ServerPlayer player, LivingEntity target) {
        Vec3 at = chest(target);
        blast(player, at, 3.0, CombatMode.punchDamage(player) * 0.8F, 1.4, 20, 0, target);
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        double resist = Math.max(0.3, 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        target.setDeltaMovement(forward.x * 2.2 * resist, 0.7 * resist, forward.z * 2.2 * resist);
        target.hurtMarked = true;
    }

    /** Jump + attack: a blast from below that rockets it straight up. */
    static void risingBlast(ServerPlayer player, LivingEntity target, boolean finisher) {
        effects(player.serverLevel(), target.position(), 1.6);
        double resist = Math.max(0.3, 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        target.setDeltaMovement(target.getDeltaMovement().x * 0.2, (finisher ? 1.5 : 1.15) * resist,
                target.getDeltaMovement().z * 0.2);
        target.hurtMarked = true;
        CombatMode.stun(target, com.tensurafragments.Config.COMBAT_STUN_TICKS.get() + 10);
    }

    /** The explosive dive lands: a blast at your feet as well as the slam's shockwave. */
    static void diveLanded(ServerPlayer player, double radius) {
        effects(player.serverLevel(), player.position().add(0, 0.3, 0), Math.max(2.5, radius * 0.6));
    }

    /** Grab and detonate: a blast in its face that throws it far. */
    static void detonate(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        Vec3 at = chest(target);
        CombatMode.strike(player, target, com.tensurafragments.Config.COMBAT_THROW_DAMAGE.get().floatValue() * 1.3F, 35,
                com.tensurafragments.Config.COMBAT_STUN_TICKS.get() + 10);
        blast(player, at, 2.5, CombatMode.punchDamage(player) * 0.6F, 1.0, 15, 0, target);
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        double resist = Math.max(0.3, 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        target.setDeltaMovement(forward.x * 1.9 * resist, 0.6 * resist, forward.z * 1.9 * resist);
        target.hurtMarked = true;
        target.getPersistentData().putLong(CombatMode.THROWN_KEY, player.level().getGameTime() + 20);
    }

    /** A burst dash: blasts behind you (the client moves you, in any direction you look). */
    static void burstDashed(ServerPlayer player) {
        Vec3 behind = player.position().add(0, 0.9, 0).subtract(player.getLookAngle().scale(0.8));
        effects(player.serverLevel(), behind, 1.0);
        player.resetFallDistance();
    }

    /** Still hovering: keeps you from taking fall damage, with blasts under your palms (and a little magicules). */
    public static void hover(ServerPlayer player) {
        if (!CombatMode.isOn(player) || !isExplosion(player) || !hasSkill(player) || CombatMode.isStunned(player)
                || player.onGround() || !Magicules.trySpend(player, HOVER_COST)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long until = HOVERING.put(player.getUUID(), now + 6);
        player.resetFallDistance();
        CombatMode.AIR_SAFE.put(player.getUUID(), now + 100);
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot() + 90).scale(0.4);
        for (Vec3 hand : new Vec3[] {player.position().add(right), player.position().subtract(right)}) {
            level.sendParticles(ParticleTypes.EXPLOSION, hand.x, hand.y + 0.4, hand.z, 1, 0.05, 0.05, 0.05, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y + 0.2, hand.z, 2, 0.1, 0.05, 0.1, 0.02);
        }
        if (until == null || until < now || now % 8 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.35F, 1.7F);
        }
    }

    public static boolean isHovering(ServerPlayer player) {
        Long until = HOVERING.get(player.getUUID());
        return until != null && until >= player.level().getGameTime() && !player.onGround();
    }

    static void clear(ServerPlayer player) {
        HOVERING.remove(player.getUUID());
    }

    // ---- The skill key ----

    /** AP Shot: a focused blast that flies straight to whatever you aim at, up to 32 blocks. */
    public static boolean apShot(ServerPlayer player) {
        if (CombatMode.isStunned(player) || !Magicules.trySpend(player, AP_SHOT_COST)) {
            return false;
        }
        CombatMode.animate(player, anim(AP_SHOT));
        CombatMode.later(player, strikeTicks(AP_SHOT, 0.3F), () -> {
            ServerLevel level = player.serverLevel();
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getLookAngle().scale(32));
            HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            EntityHitResult entity = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player,
                    eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player));
            Vec3 at = entity != null ? entity.getLocation() : end;
            // The shot's trail.
            Vec3 step = at.subtract(eye);
            int points = (int) Math.ceil(step.length());
            for (int i = 1; i < points; i++) {
                Vec3 p = eye.add(step.scale(i / (double) points));
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
            blast(player, at, 1.8, 10, 1.0, 25, com.tensurafragments.Config.COMBAT_STUN_TICKS.get(), null);
        });
        return true;
    }

    /** Stun Grenade: a blinding flash in front of you that stuns and blinds everything it catches. */
    public static boolean stunGrenade(ServerPlayer player) {
        if (CombatMode.isStunned(player) || !Magicules.trySpend(player, STUN_GRENADE_COST)) {
            return false;
        }
        CombatMode.animate(player, anim(STUN_GRENADE));
        CombatMode.later(player, strikeTicks(STUN_GRENADE, 0.35F), () -> {
            ServerLevel level = player.serverLevel();
            Vec3 look = player.getLookAngle();
            Vec3 at = player.getEyePosition().add(look.scale(1.5));
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.3, 0.3, 0.3, 0.35);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.4F, 1.6F);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(7),
                    e -> e != player && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player))) {
                Vec3 to = target.getEyePosition().subtract(player.getEyePosition());
                if (to.length() > 7 || to.normalize().dot(look) < 0.3) {
                    continue;
                }
                CombatMode.strike(player, target, 2 * (CombatMode.isOn(player) ? 1 : EpScaling.multiplier(player)), 20, 50);
                CombatMode.stun(target, 50);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
            }
        });
        return true;
    }

    /** Howitzer Impact: launch yourself up and forward, spinning, and come down as a huge explosion. */
    public static boolean howitzer(ServerPlayer player) {
        if (CombatMode.isStunned(player) || !Magicules.trySpend(player, HOWITZER_COST)) {
            return false;
        }
        CombatMode.animate(player, anim(HOWITZER));
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        player.setDeltaMovement(forward.x * 0.9, 0.95, forward.z * 0.9);
        player.hurtMarked = true;
        CombatMode.AIR_SAFE.put(player.getUUID(), player.level().getGameTime() + 100);
        int strike = strikeTicks(HOWITZER, 0.9F);
        // The spin: little blasts all around as you go.
        for (int t = 3; t < strike; t += 3) {
            CombatMode.later(player, t, () -> {
                Vec3 side = Vec3.directionFromRotation(0, player.level().getGameTime() * 60F).scale(0.9);
                effects(player.serverLevel(), player.position().add(side).add(0, 0.8, 0), 0.6);
            });
        }
        CombatMode.later(player, strike, () -> {
            player.resetFallDistance();
            Vec3 at = player.position().add(player.getLookAngle().scale(1.5)).add(0, 0.5, 0);
            blast(player, at, 5.0, 24, 2.2, 60, 20, null);
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 0.2, Math.max(motion.y, 0.3), motion.z * 0.2);
            player.hurtMarked = true;
        });
        return true;
    }
}
