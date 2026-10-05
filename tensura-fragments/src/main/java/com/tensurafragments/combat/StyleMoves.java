package com.tensurafragments.combat;

import com.tensurafragments.Config;
import com.tensurafragments.ally.Allies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The fighting styles' own moves (Brawler's are in {@link CombatMode}):
 * <ul>
 * <li>Swift: spin kick (jump + attack), dive kick (air sneak + attack), counter stance (grab key), quick step (dash
 * key), whirlwind finisher on the 5th hit</li>
 * <li>Titan: hammer fist, meteor slam, tackle, iron body, ground pound finisher on the 3rd hit</li>
 * <li>Ki: ki palm, ki bomb, ki burst (works even while stunned: a combo breaker), vanish, ki blast finisher</li>
 * </ul>
 */
public final class StyleMoves {
    private record Dive(Vec3 direction, long started, Vec3 last) {
    }

    private record Tackle(Vec3 direction, long started, Set<Integer> hit) {
    }

    private static final Map<UUID, Dive> DIVES = new HashMap<>();
    private static final Map<UUID, Tackle> TACKLES = new HashMap<>();
    /** Swift's counter stance: until when it's up. */
    private static final Map<UUID, Long> COUNTERS = new HashMap<>();
    private static final Map<UUID, Long> IRON_BODY = new HashMap<>();
    /** When each player's moves are ready again, by move. */
    private static final Map<String, Long> READY = new HashMap<>();

    static final int DIVE_TICKS = 30;
    static final int TACKLE_TICKS = 8;
    static final int COUNTER_TICKS = 12;
    static final int IRON_BODY_TICKS = 40;

    private StyleMoves() {
    }

    static void clear(ServerPlayer player) {
        DIVES.remove(player.getUUID());
        TACKLES.remove(player.getUUID());
        COUNTERS.remove(player.getUUID());
        IRON_BODY.remove(player.getUUID());
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    /** Starts a move's cooldown, or says no if it's still cooling down. */
    private static boolean ready(ServerPlayer player, String move, int cooldown) {
        String key = player.getUUID() + move;
        if (now(player) < READY.getOrDefault(key, 0L)) {
            return false;
        }
        READY.put(key, now(player) + cooldown);
        return true;
    }

    /** Super armour: no stun, no knockback (Titan's tackle and iron body). */
    public static boolean isArmored(ServerPlayer player) {
        return isIronBody(player) || TACKLES.containsKey(player.getUUID());
    }

    public static boolean isIronBody(ServerPlayer player) {
        Long until = IRON_BODY.get(player.getUUID());
        return until != null && now(player) < until;
    }

    public static boolean isDiving(ServerPlayer player) {
        return DIVES.containsKey(player.getUUID());
    }

    public static boolean isTackling(ServerPlayer player) {
        return TACKLES.containsKey(player.getUUID());
    }

    public static boolean isCountering(ServerPlayer player) {
        Long until = COUNTERS.get(player.getUUID());
        return until != null && now(player) < until;
    }

    private static Vec3 forward(ServerPlayer player) {
        return Vec3.directionFromRotation(0, player.getYRot());
    }

    /** Moves a player and turns them (a real player's client is told; a test player just moves). */
    static void warp(ServerPlayer player, Vec3 to, float yaw, float pitch) {
        if (player instanceof FakePlayer || player.connection == null) {
            player.moveTo(to.x, to.y, to.z, yaw, pitch);
        } else {
            player.connection.teleport(to.x, to.y, to.z, yaw, pitch);
        }
        player.setYHeadRot(yaw);
        player.resetFallDistance();
    }

    private static float yawToward(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
    }

    private static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 centre, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius, radius * 0.6 + 1, radius),
                e -> e != player && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player)
                        && e.position().distanceTo(centre) <= radius + e.getBbWidth() / 2);
    }

    private static void push(LivingEntity target, Vec3 direction, double horizontal, double up) {
        double resist = Math.max(0.3, 1 - target.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        Vec3 flat = direction.multiply(1, 0, 1);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        target.setDeltaMovement(flat.x * horizontal * resist, up * resist, flat.z * horizontal * resist);
        target.hurtMarked = true;
    }

    // ---- Shapes ----

    /** A flat white ring. */
    static void ring(ServerLevel level, Vec3 centre, double radius, float thickness, int life, boolean sweep) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            double angle = i * Math.PI * 2 / 40;
            points.add(centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        CombatMode.arc(level, points, thickness, life, sweep);
    }

    /** A straight white streak. */
    static void streak(ServerLevel level, Vec3 from, Vec3 to, float thickness, int life) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            points.add(from.lerp(to, i / 12.0));
        }
        CombatMode.arc(level, points, thickness, life, true);
    }

    // ---- Launchers (jump + attack) ----

    /** The swish each style's jumping move draws as it's thrown. */
    static void launcherVisual(ServerLevel level, ServerPlayer player, FightingStyle style) {
        Vec3 forward = forward(player);
        switch (style) {
            case SWIFT -> ring(level, player.position().add(0, 0.9, 0), 1.6, 0.18F, 6, true);
            case TITAN -> {
                // An overhead swing down in front.
                List<Vec3> points = new ArrayList<>();
                Vec3 base = player.position().add(forward.scale(0.9));
                for (int i = 0; i < 24; i++) {
                    double t = i / 23.0;
                    points.add(base.add(forward.scale(Math.sin(t * Math.PI) * 0.6)).add(0, 2.6 - t * 2.4, 0));
                }
                CombatMode.arc(level, points, 0.28F, 6, true);
            }
            case KI -> {
                Vec3 hand = player.getEyePosition().subtract(0, 0.4, 0);
                streak(level, hand, hand.add(player.getLookAngle().scale(3.5)), 0.22F, 6);
            }
            default -> CombatMode.upswish(level, player);
        }
    }

    /** What a jumping punch does to what it hits, by style (Brawler's uppercut is in CombatMode). */
    static void launcher(ServerPlayer player, LivingEntity target, FightingStyle style, boolean finisher) {
        ServerLevel level = player.serverLevel();
        int stun = Config.COMBAT_STUN_TICKS.get();
        switch (style) {
            case SWIFT -> {
                // Spin kick: the target and everything around you.
                push(target, target.position().subtract(player.position()), 0.6, 0.35);
                CombatMode.stun(target, stun + 6);
                for (LivingEntity other : enemiesAround(player, player.position(), 2.6)) {
                    if (other != target && CombatMode.strike(player, other, CombatMode.punchDamage(player) * 0.8F, 15, stun)) {
                        push(other, other.position().subtract(player.position()), 0.6, 0.35);
                    }
                }
            }
            case TITAN -> {
                // Hammer fist: spiked down out of the air, or bounced off the ground.
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x * 0.2, target.onGround() ? 0.55 : -1.6, motion.z * 0.2);
                target.hurtMarked = true;
                CombatMode.stun(target, stun + 14);
                ring(level, target.position().add(0, 0.1, 0), 2.0, 0.16F, 6, false);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND,
                        SoundSource.PLAYERS, 0.5F, 0.7F);
                for (LivingEntity other : enemiesAround(player, target.position(), 2.0)) {
                    if (other != target) {
                        CombatMode.strike(player, other, 3, 12, stun);
                    }
                }
            }
            case KI -> {
                // Ki palm: blasted straight away.
                push(target, player.getLookAngle(), finisher ? 2.6 : 2.2, 0.3);
                CombatMode.stun(target, stun + 8);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST,
                        SoundSource.PLAYERS, 1.0F, 0.7F);
            }
            default -> {
            }
        }
    }

    // ---- Finishers ----

    static void finisher(ServerPlayer player, LivingEntity target, FightingStyle style) {
        ServerLevel level = player.serverLevel();
        int stun = Config.COMBAT_STUN_TICKS.get();
        switch (style) {
            case SWIFT -> {
                // Whirlwind: everything around you, the target hardest.
                CombatMode.launch(player, target);
                ring(level, player.position().add(0, 0.6, 0), 2.4, 0.24F, 7, true);
                ring(level, player.position().add(0, 1.3, 0), 2.0, 0.18F, 7, true);
                for (LivingEntity other : enemiesAround(player, player.position(), 2.6)) {
                    if (other != target && CombatMode.strike(player, other, CombatMode.punchDamage(player), 15, stun)) {
                        push(other, other.position().subtract(player.position()), 0.9, 0.45);
                    }
                }
            }
            case TITAN -> {
                // Ground pound: flung far, and a shockwave round where it stood.
                push(target, target.position().subtract(player.position()), 1.7, 0.8);
                ring(level, target.position().add(0, 0.1, 0), 2.5, 0.2F, 8, false);
                ring(level, target.position().add(0, 0.1, 0), 1.4, 0.2F, 8, false);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.PLAYERS, 0.6F, 0.8F);
                for (LivingEntity other : enemiesAround(player, target.position(), 2.5)) {
                    if (other != target && CombatMode.strike(player, other, CombatMode.punchDamage(player) * 0.8F, 20, stun)) {
                        push(other, other.position().subtract(target.position()), 0.7, 0.5);
                    }
                }
            }
            case KI -> {
                // Ki blast: a beam that throws it far away.
                push(target, player.getLookAngle(), 2.6, 0.5);
                Vec3 hand = player.getEyePosition().subtract(0, 0.4, 0);
                streak(level, hand, hand.add(player.getLookAngle().scale(7)), 0.34F, 8);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                        SoundSource.PLAYERS, 1.2F, 0.6F);
            }
            default -> CombatMode.launch(player, target);
        }
    }

    // ---- Air moves (sneak + attack in mid-air) ----

    /** How fast a dive kick travels (blocks a tick). */
    static final double DIVE_SPEED = 1.5;

    /**
     * Swift: kick down and forward at a slant (straight at the nearest enemy in front of you, if there is one); the
     * first thing it meets is spiked and you bounce off.
     */
    static boolean diveKick(ServerPlayer player) {
        if (player.onGround() || player.isInWater() || DIVES.containsKey(player.getUUID())) {
            return false;
        }
        Vec3 dir = diveDirection(player);
        DIVES.put(player.getUUID(), new Dive(dir, now(player), player.position()));
        player.setDeltaMovement(dir.scale(DIVE_SPEED));
        player.hurtMarked = true;
        CombatMode.AIR_SAFE.put(player.getUUID(), now(player) + 100);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_SWOOP,
                SoundSource.PLAYERS, 0.8F, 1.8F);
        return true;
    }

    /** Down at a slant, or homing in on the nearest enemy within 7 blocks in front and below. */
    static Vec3 diveDirection(ServerPlayer player) {
        Vec3 look = forward(player);
        Vec3 from = player.position().add(0, 0.5, 0);
        LivingEntity aim = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : enemiesAround(player, player.position(), 7)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(from);
            Vec3 flat = to.multiply(1, 0, 1);
            if (to.y > 1.0 || flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(look) < 0.5) {
                continue;
            }
            if (to.lengthSqr() < best) {
                best = to.lengthSqr();
                aim = e;
            }
        }
        if (aim == null) {
            return new Vec3(look.x * 0.76, -0.65, look.z * 0.76).normalize();
        }
        Vec3 to = aim.getBoundingBox().getCenter().subtract(from);
        // Always at least a little downward, so it's a dive.
        return new Vec3(to.x, Math.min(to.y, -0.3 * to.length()), to.z).normalize();
    }

    /** Ki: a blast at the ground below; you hang in the air while it goes off. */
    static boolean kiBomb(ServerPlayer player) {
        if (player.onGround() || !ready(player, "kibomb", 30)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -24, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        Vec3 at = hit.getLocation();
        streak(level, player.getEyePosition().subtract(0, 0.5, 0), at, 0.26F, 6);
        ring(level, at.add(0, 0.15, 0), 1.5, 0.18F, 8, false);
        ring(level, at.add(0, 0.15, 0), 3.0, 0.18F, 8, false);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 16, 1.2, 0.1, 1.2, 0.06);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
        float damage = Config.COMBAT_SLAM_DAMAGE.get().floatValue() * 0.8F;
        for (LivingEntity target : enemiesAround(player, at, 3.0)) {
            if (CombatMode.strike(player, target, damage, 20, Config.COMBAT_STUN_TICKS.get())) {
                push(target, target.position().subtract(at), 0.5, 0.55);
            }
        }
        player.setDeltaMovement(player.getDeltaMovement().x * 0.5, 0.35, player.getDeltaMovement().z * 0.5);
        player.hurtMarked = true;
        CombatMode.AIR_SAFE.put(player.getUUID(), now(player) + 100);
        return true;
    }

    // ---- Grab key ----

    /** The grab key: Brawler grabs, Swift takes a counter stance, Titan tackles, Ki bursts. */
    public static boolean grabKey(ServerPlayer player) {
        FightingStyle style = CombatMode.style(player);
        if (style == FightingStyle.KI) {
            return kiBurst(player);
        }
        if (!CombatMode.isOn(player) || CombatMode.isStunned(player)) {
            return false;
        }
        return switch (style) {
            case SWIFT -> counterStance(player);
            case TITAN -> tackle(player);
            default -> CombatMode.grabOrThrow(player);
        };
    }

    /** Swift: for a moment, a hit on you is turned aside and answered from behind. */
    static boolean counterStance(ServerPlayer player) {
        if (!ready(player, "counter", 40)) {
            return false;
        }
        COUNTERS.put(player.getUUID(), now(player) + COUNTER_TICKS);
        CombatMode.guard(player.serverLevel(), player, false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(),
                SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    /** A hit lands while the counter stance is up: step behind the attacker and strike. */
    static boolean counter(ServerPlayer player, LivingEntity attacker) {
        if (!isCountering(player) || attacker == player || Allies.isFriendly(attacker, player)
                || attacker.distanceTo(player) > 8) {
            return false;
        }
        COUNTERS.remove(player.getUUID());
        Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? forward(player) : away.normalize();
        Vec3 behind = attacker.position().add(away.scale(attacker.getBbWidth() / 2 + 0.9));
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        warp(player, behind, yawToward(behind, attacker.position()), 0);
        CombatMode.swish(level, player, true, true);
        CombatMode.strike(player, attacker, CombatMode.punchDamage(player) * 1.5F + 2, 35, Config.COMBAT_STUN_TICKS.get() + 10);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                1.2F, 1.2F);
        player.displayClientMessage(Component.translatable("tensurafragments.combat.counter").withStyle(ChatFormatting.GOLD),
                true);
        return true;
    }

    /** Titan: charge forward, shrugging off hits, bowling over everything in the way. */
    static boolean tackle(ServerPlayer player) {
        if (TACKLES.containsKey(player.getUUID()) || !ready(player, "tackle", 60)) {
            return false;
        }
        TACKLES.put(player.getUUID(), new Tackle(forward(player), now(player), new HashSet<>()));
        CombatMode.BLOCKING.remove(player.getUUID());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    /**
     * Ki: a burst of ki all around that throws everything off and knocks it down. It works even while you're stunned or
     * held, so it breaks combos.
     */
    static boolean kiBurst(ServerPlayer player) {
        if (!CombatMode.isOn(player) || !ready(player, "kiburst", 200)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        player.getPersistentData().remove("tensurafragments_stun_until");
        player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        CombatMode.GRABS.values().removeIf(grab -> grab.target() == player);
        Vec3 centre = player.position().add(0, 0.9, 0);
        ring(level, centre, 1.5, 0.22F, 7, false);
        ring(level, centre, 3.0, 0.2F, 7, false);
        ring(level, centre, 4.5, 0.18F, 7, false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS,
                1.0F, 1.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.5F, 1.6F);
        for (LivingEntity target : enemiesAround(player, player.position(), 4.5)) {
            CombatMode.strike(player, target, 3, 100, 0);
            push(target, target.position().subtract(player.position()), 1.3, 0.45);
        }
        return true;
    }

    // ---- Dash key ----

    /** The dash key: Brawler dashes, Swift quick-steps, Titan hardens, Ki vanishes. */
    public static boolean dashKey(ServerPlayer player, Vec3 direction) {
        return switch (CombatMode.style(player)) {
            case TITAN -> ironBody(player);
            case KI -> vanish(player);
            default -> CombatMode.dash(player, direction);
        };
    }

    /** Titan: two seconds of iron skin: no stun or knockback, and hits do less. */
    static boolean ironBody(ServerPlayer player) {
        if (!CombatMode.isOn(player) || CombatMode.isStunned(player) || !ready(player, "iron", 160)) {
            return false;
        }
        IRON_BODY.put(player.getUUID(), now(player) + IRON_BODY_TICKS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_PLACE,
                SoundSource.PLAYERS, 0.6F, 0.6F);
        player.displayClientMessage(Component.translatable("tensurafragments.combat.iron_body").withStyle(ChatFormatting.GRAY),
                true);
        return true;
    }

    /** Ki: vanish and reappear behind what you're looking at (or a few blocks ahead), untouchable for a moment. */
    static boolean vanish(ServerPlayer player) {
        if (!CombatMode.isOn(player) || CombatMode.isStunned(player) || CombatMode.isGrabbing(player)
                || !ready(player, "vanish", 30)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = player.getViewVector(1F).scale(16);
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, player, eye, eye.add(reach), player.getBoundingBox().expandTowards(reach).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player));
        Vec3 to;
        float yaw;
        if (hit != null) {
            Entity target = hit.getEntity();
            Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? forward(player) : away.normalize();
            to = target.position().add(away.scale(target.getBbWidth() / 2 + 0.9));
            yaw = yawToward(to, target.position());
        } else {
            Vec3 ahead = forward(player).scale(6);
            BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(ahead), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            Vec3 stop = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation().subtract(forward(player).scale(0.6))
                    : eye.add(ahead);
            to = new Vec3(stop.x, player.getY(), stop.z);
            yaw = player.getYRot();
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.7F, 1.8F);
        CombatMode.DASHED.put(player.getUUID(), now(player));
        CombatMode.BLOCKING.remove(player.getUUID());
        warp(player, to, yaw, player.getXRot());
        level.sendParticles(ParticleTypes.CLOUD, to.x, to.y + 1, to.z, 10, 0.3, 0.6, 0.3, 0.02);
        return true;
    }

    // ---- Every tick ----

    /** Keeps dives and tackles going, and shows iron body. */
    public static void tick(ServerPlayer player) {
        tickDive(player);
        tickTackle(player);
        if (isIronBody(player) && player.tickCount % 4 == 0) {
            ring(player.serverLevel(), player.position().add(0, 0.2, 0), 0.8, 0.08F, 5, false);
            ring(player.serverLevel(), player.position().add(0, 1.2, 0), 0.7, 0.08F, 5, false);
        }
    }

    public static void tickDive(ServerPlayer player) {
        Dive dive = DIVES.get(player.getUUID());
        if (dive == null) {
            return;
        }
        boolean landed = player.onGround() || player.isInWater();
        if (!player.isAlive() || now(player) - dive.started() > DIVE_TICKS) {
            DIVES.remove(player.getUUID());
            return;
        }
        // Everything along the way since last tick (at this speed it'd skip right past things), a little ahead, and on
        // landing, a kick at whatever's right there.
        Vec3 moved = dive.last().subtract(player.position());
        AABB path = player.getBoundingBox().minmax(player.getBoundingBox().move(moved))
                .expandTowards(dive.direction().scale(0.8)).inflate(landed ? 1.2 : 0.7);
        if (!landed) {
            DIVES.put(player.getUUID(), new Dive(dive.direction(), dive.started(), player.position()));
            player.setDeltaMovement(dive.direction().scale(DIVE_SPEED));
            player.hurtMarked = true;
            player.resetFallDistance();
        }
        LivingEntity hit = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, path,
                e -> e != player && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player))) {
            double d = target.distanceToSqr(dive.last());
            if (d < nearest) {
                nearest = d;
                hit = target;
            }
        }
        if (hit == null) {
            if (landed) {
                DIVES.remove(player.getUUID());
            }
            return;
        }
        LivingEntity target = hit;
        DIVES.remove(player.getUUID());
        CombatMode.strike(player, target, CombatMode.punchDamage(player) * 1.5F + 3, 25, Config.COMBAT_STUN_TICKS.get() + 6);
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(dive.direction().x * 0.6 + motion.x * 0.2, -0.5, dive.direction().z * 0.6 + motion.z * 0.2);
        target.hurtMarked = true;
        // Bounce off it, back up into the air.
        player.setDeltaMovement(-dive.direction().x * 0.3, 0.6, -dive.direction().z * 0.3);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        streak(level, player.position().subtract(dive.direction().scale(2)).add(0, 2, 0), target.position().add(0, 1, 0),
                0.2F, 6);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 1.2F, 1.2F);
    }

    public static void tickTackle(ServerPlayer player) {
        Tackle tackle = TACKLES.get(player.getUUID());
        if (tackle == null) {
            return;
        }
        if (!player.isAlive() || now(player) - tackle.started() >= TACKLE_TICKS) {
            TACKLES.remove(player.getUUID());
            return;
        }
        Vec3 dir = tackle.direction();
        player.setDeltaMovement(dir.x * 1.1, Math.min(player.getDeltaMovement().y, 0), dir.z * 1.1);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().expandTowards(dir.scale(1.2)).inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player))) {
            if (tackle.hit().add(target.getId())
                    && CombatMode.strike(player, target, CombatMode.punchDamage(player) + 4, 30, Config.COMBAT_STUN_TICKS.get() + 12)) {
                push(target, dir, 1.3, 0.45);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        SoundSource.PLAYERS, 1.2F, 0.6F);
            }
        }
    }
}
