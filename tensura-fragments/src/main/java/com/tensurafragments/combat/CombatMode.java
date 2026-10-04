package com.tensurafragments.combat;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ally.Allies;
import com.tensurafragments.network.SyncCombatPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Combat Mode, toggled per player: melee hits chain into combos (left and right jabs, a little lunge, white swishes),
 * every hit stuns what it hits (it can't move or hurt anyone for a moment, and hangs in the air if it's airborne), the
 * last hit of a combo is a finisher that launches, a jumping punch is an uppercut that carries you up with the target,
 * and attacking while sneaking in mid-air slams you down into a shockwave. You can also block (a block started just
 * before a hit parries it), grab and throw, and dash (briefly untouchable).
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class CombatMode {
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1F, 1F, 1F), 1.0F);
    private static final DustParticleOptions WHITE_BIG = new DustParticleOptions(new Vector3f(1F, 1F, 1F), 1.4F);
    private static final String STUN_KEY = "tensurafragments_stun_until";
    private static final String LANDED_KEY = "tensurafragments_slam_landed";
    /** How long a slam may take to land before it's called off. */
    private static final int SLAM_TIMEOUT = 100;

    private record Combo(int count, long lastHit) {
    }

    private record Slam(double fromY, long started) {
    }

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();
    private static final Map<UUID, Slam> SLAMS = new HashMap<>();
    /** The finisher being dealt right now (for its damage and knockback). */
    private static final Map<UUID, Boolean> FINISHING = new HashMap<>();
    /** When the client said the next punch is an uppercut (jumping while punching). */
    private static final Map<UUID, Long> UPPERCUT_ASKED = new HashMap<>();
    /** The uppercut being dealt right now. */
    private static final Map<UUID, Boolean> UPPERCUTTING = new HashMap<>();
    /** Players blocking, and when they started (for parries). */
    private static final Map<UUID, Long> BLOCKING = new HashMap<>();
    /** What each player is holding, and since when. */
    private static final Map<UUID, Grab> GRABS = new HashMap<>();
    /** When each player last dashed. */
    private static final Map<UUID, Long> DASHED = new HashMap<>();
    /** Players riding an uppercut up: no fall damage until they land (or this time passes). */
    private static final Map<UUID, Long> AIR_SAFE = new HashMap<>();
    /** How long a grab holds before letting go. */
    private static final int GRAB_TICKS = 40;

    private record Grab(LivingEntity target, long started) {
    }

    private CombatMode() {
    }

    public static boolean isOn(ServerPlayer player) {
        return Config.COMBAT_ENABLED.get() && player.getData(ModRegistries.COMBAT_MODE);
    }

    public static void toggle(ServerPlayer player) {
        if (!Config.COMBAT_ENABLED.get()) {
            player.displayClientMessage(Component.translatable("tensurafragments.combat.disabled"), true);
            return;
        }
        boolean on = !player.getData(ModRegistries.COMBAT_MODE);
        player.setData(ModRegistries.COMBAT_MODE, on);
        COMBOS.remove(player.getUUID());
        BLOCKING.remove(player.getUUID());
        GRABS.remove(player.getUUID());
        player.displayClientMessage(Component.translatable(on ? "tensurafragments.combat.on" : "tensurafragments.combat.off")
                .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.PLAYERS, 0.8F, on ? 1.3F : 0.8F);
        sync(player, 0);
    }

    public static void sync(ServerPlayer player, int combo) {
        PacketDistributor.sendToPlayer(player, new SyncCombatPayload(isOn(player), combo));
    }

    public static int combo(ServerPlayer player) {
        Combo combo = COMBOS.get(player.getUUID());
        return combo == null || player.level().getGameTime() - combo.lastHit() > Config.COMBAT_COMBO_WINDOW_TICKS.get()
                ? 0 : combo.count();
    }

    // ---- Hit stun ----

    public static boolean isStunned(Entity entity) {
        return entity.getPersistentData().getLong(STUN_KEY) > entity.level().getGameTime();
    }

    public static void stun(LivingEntity target, int ticks) {
        if (ticks <= 0) {
            return;
        }
        target.getPersistentData().putLong(STUN_KEY, target.level().getGameTime() + ticks);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, false));
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    /** Stunned creatures stay put, and hang in the air if they're airborne (so they can be juggled). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide || !isStunned(living)) {
            return;
        }
        Vec3 motion = living.getDeltaMovement();
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            // Only its own walking is held back: launches (finisher, uppercut, slam) still carry it up.
            if (motion.y <= 0.1) {
                mob.setDeltaMovement(motion.x * 0.5, motion.y, motion.z * 0.5);
            }
        }
        if (!living.onGround() && !living.isInWater() && motion.y < -AIR_HANG && !isHeld(living)) {
            living.setDeltaMovement(living.getDeltaMovement().x, -AIR_HANG, living.getDeltaMovement().z);
            living.resetFallDistance();
            if (living instanceof ServerPlayer) {
                living.hurtMarked = true;
            }
        }
    }

    /** How fast (blocks a tick) a stunned creature sinks in mid-air. */
    static final double AIR_HANG = 0.05;

    /** ...and can't hurt anyone. Dashing players can't be hurt either, and blocking ones mostly not from the front. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer target && isDodging(target)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            target.serverLevel().sendParticles(WHITE, target.getX(), target.getY() + 1, target.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            return;
        }
        if (attacker != null && event.getSource().getDirectEntity() == attacker && !attacker.level().isClientSide
                && isStunned(attacker)) {
            event.setCanceled(true);
            return;
        }
        // The finisher hits harder.
        if (attacker instanceof ServerPlayer player && FINISHING.containsKey(player.getUUID())) {
            event.setAmount((float) (event.getAmount() * Config.COMBAT_FINISHER_DAMAGE.get() + 3));
        }
        if (attacker instanceof ServerPlayer player && UPPERCUTTING.containsKey(player.getUUID())) {
            event.setAmount(event.getAmount() * 1.2F + 2);
        }
        if (event.getEntity() instanceof ServerPlayer target && isBlocking(target) && blocksFrom(target, event.getSource())) {
            block(event, target, attacker);
        }
    }

    // ---- Punches and combos ----

    private static boolean isMelee(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        return source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK);
    }

    /** Before a melee hit lands: count the combo, and mark the finisher. */
    @SubscribeEvent
    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isOn(player) || !(event.getTarget() instanceof LivingEntity)
                || Allies.isFriendly(event.getTarget(), player)) {
            return;
        }
        if (GRABS.containsKey(player.getUUID())) {
            // Punching while holding something throws it.
            event.setCanceled(true);
            throwHeld(player);
            return;
        }
        int count = combo(player) + 1;
        int finisher = Config.COMBAT_FINISHER_HIT.get();
        COMBOS.put(player.getUUID(), new Combo(count >= finisher ? 0 : count, player.level().getGameTime()));
        if (count >= finisher) {
            FINISHING.put(player.getUUID(), true);
        } else {
            FINISHING.remove(player.getUUID());
        }
        // (Which hand jabs is the client's: it alternates left and right itself.)
        boolean left = count % 2 == 0;
        Long asked = UPPERCUT_ASKED.remove(player.getUUID());
        boolean uppercut = asked != null && player.level().getGameTime() - asked <= 5;
        if (uppercut) {
            UPPERCUTTING.put(player.getUUID(), true);
            // You go up with it, a touch slower so it stays just above you for the next hit.
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            player.setDeltaMovement(forward.x * 0.1, uppercutPower(count >= finisher) * 0.95, forward.z * 0.1);
            player.hurtMarked = true;
            AIR_SAFE.put(player.getUUID(), player.level().getGameTime() + 100);
            upswish((ServerLevel) player.level(), player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    SoundSource.PLAYERS, 1.1F, 0.8F);
        } else {
            UPPERCUTTING.remove(player.getUUID());
            // A small step in with each jab.
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            double lunge = count >= finisher ? 0.45 : 0.15;
            if (player.onGround() || isSlamming(player)) {
                player.push(forward.x * lunge, 0, forward.z * lunge);
            } else {
                // In mid-air each hit keeps you up, so you can keep hitting what's hanging there.
                player.setDeltaMovement(forward.x * lunge, AIR_HIT_LIFT, forward.z * lunge);
                AIR_SAFE.put(player.getUUID(), player.level().getGameTime() + 100);
            }
            player.hurtMarked = true;
            swish((ServerLevel) player.level(), player, left, count >= finisher);
        }
        sync(player, count >= finisher ? finisher : count);
    }

    /** After it lands: stun, and the finisher's launch. Rapid hits aren't swallowed by the target's hit cooldown. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !isOn(player)
                || !isMelee(player, event.getSource())) {
            return;
        }
        LivingEntity target = event.getEntity();
        boolean finisher = FINISHING.containsKey(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (target instanceof ServerPlayer blocker && isBlocking(blocker)) {
            // A held block isn't stunned (a broken one already was).
            hitSpark(level, target);
            return;
        }
        if (finisher) {
            // The launch itself comes with the knockback, just after this.
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() / 2,
                    target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    16, 0.3, 0.3, 0.3, 0.4);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.2F, 0.7F);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.4F, 1.8F);
            stun(target, Config.COMBAT_STUN_TICKS.get() + 10);
        } else {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.8F, 1.3F);
            stun(target, Config.COMBAT_STUN_TICKS.get());
            target.invulnerableTime = 0;
        }
        hitSpark(level, target);
    }

    /** Combo hits keep the target close; the finisher launches it up and away instead. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof ServerPlayer blocker && isBlocking(blocker)) {
            event.setStrength(event.getStrength() * 0.3F);
            return;
        }
        if (isHeld(target)) {
            event.setCanceled(true);
            return;
        }
        if (!(target.getLastHurtByMob() instanceof ServerPlayer player) || !isOn(player)
                || target.getLastHurtByMobTimestamp() != target.tickCount) {
            return;
        }
        boolean finisher = FINISHING.remove(player.getUUID()) != null;
        if (UPPERCUTTING.remove(player.getUUID()) != null) {
            event.setCanceled(true);
            uppercutLaunch(target, uppercutPower(finisher));
            stun(target, Config.COMBAT_STUN_TICKS.get() + 8);
        } else if (finisher) {
            event.setCanceled(true);
            launch(player, target);
        } else {
            event.setStrength(event.getStrength() * 0.25F);
        }
    }

    /** The client says this punch is thrown while jumping: an uppercut. */
    public static void markUppercut(ServerPlayer player) {
        if (isOn(player)) {
            UPPERCUT_ASKED.put(player.getUUID(), player.level().getGameTime());
        }
    }

    /** How hard an uppercut launches (the finisher's harder). */
    static double uppercutPower(boolean finisher) {
        return finisher ? 1.45 : 1.1;
    }

    /** Upward speed each mid-air hit gives you. */
    static final double AIR_HIT_LIFT = 0.25;

    /** Straight up. */
    static void uppercutLaunch(LivingEntity target, double power) {
        double resist = 1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        target.setDeltaMovement(target.getDeltaMovement().x * 0.2, power * Math.max(0.3, resist),
                target.getDeltaMovement().z * 0.2);
        target.hurtMarked = true;
    }

    /** A white swish rising from the hip up past the head, in front of the player. */
    static void upswish(ServerLevel level, ServerPlayer player) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 base = player.position().add(forward.scale(0.9));
        for (int i = 0; i < 16; i++) {
            double t = i / 15.0;
            double arc = Math.sin(t * Math.PI) * 0.45;
            Vec3 at = base.add(forward.scale(arc)).add(0, 0.4 + t * 2.0, 0);
            level.sendParticles(WHITE_BIG, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void launch(ServerPlayer player, LivingEntity target) {
        Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : away.normalize();
        double resist = 1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        target.setDeltaMovement(away.x * 1.4 * resist, 0.75 * Math.max(0.3, resist), away.z * 1.4 * resist);
        target.hurtMarked = true;
    }

    /** A white swish across the front of the player, right to left or left to right. */
    static void swish(ServerLevel level, ServerPlayer player, boolean fromLeft, boolean big) {
        Vec3 eye = player.getEyePosition().subtract(0, 0.35, 0);
        float yaw = player.getYRot();
        int points = big ? 22 : 15;
        double radius = big ? 1.7 : 1.25;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            double angle = Math.toRadians(yaw + (fromLeft ? -1 : 1) * (70 - 140 * t));
            Vec3 at = eye.add(-Math.sin(angle) * radius, (t - 0.5) * (big ? 0.5 : 0.25), Math.cos(angle) * radius);
            level.sendParticles(big ? WHITE_BIG : WHITE, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    private static void hitSpark(ServerLevel level, LivingEntity target) {
        level.sendParticles(WHITE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8,
                0.15, 0.15, 0.15, 0.05);
    }

    // ---- Down slam ----

    /** Sneak and attack in mid-air: dive straight down. */
    public static boolean slam(ServerPlayer player) {
        if (!isOn(player) || player.onGround() || player.isInWater() || SLAMS.containsKey(player.getUUID())) {
            return false;
        }
        SLAMS.put(player.getUUID(), new Slam(player.getY(), player.level().getGameTime()));
        player.setDeltaMovement(player.getDeltaMovement().x * 0.2, -2.6, player.getDeltaMovement().z * 0.2);
        player.hurtMarked = true;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_SWOOP,
                SoundSource.PLAYERS, 0.9F, 1.4F);
        return true;
    }

    /** Lands the slam: a shockwave that hurts and knocks up everything around, harder the further you fell. */
    public static void land(ServerPlayer player) {
        Slam slam = SLAMS.remove(player.getUUID());
        if (slam == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        player.resetFallDistance();
        player.getPersistentData().putLong(LANDED_KEY, level.getGameTime());
        double height = Math.max(0, slam.fromY() - player.getY());
        double radius = Config.COMBAT_SLAM_RADIUS.get();
        float damage = (float) (Config.COMBAT_SLAM_DAMAGE.get() + Math.min(height, 30) * 0.4);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1.5, radius),
                e -> e != player && e.isAlive() && !Allies.isFriendly(e, player))) {
            double distance = target.distanceTo(player);
            if (distance > radius + 0.5) {
                continue;
            }
            float falloff = (float) Math.max(0.35, 1 - distance / (radius + 1));
            target.hurt(player.damageSources().playerAttack(player), damage * falloff);
            Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.6 * falloff);
            target.setDeltaMovement(away.x, 0.65 * falloff + 0.2, away.z);
            target.hurtMarked = true;
            stun(target, Config.COMBAT_STUN_TICKS.get());
        }
        // A ring of white, dust and a thud.
        for (int i = 0; i < 36; i++) {
            double angle = i * Math.PI * 2 / 36;
            for (double r = 1; r <= radius; r += radius / 2.5) {
                level.sendParticles(WHITE_BIG, player.getX() + Math.cos(angle) * r, player.getY() + 0.15,
                        player.getZ() + Math.sin(angle) * r, 1, 0, 0.05, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 20, radius / 3, 0.1,
                radius / 3, 0.08);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                        level.getBlockState(player.blockPosition().below())),
                player.getX(), player.getY() + 0.1, player.getZ(), 40, radius / 2, 0.1, radius / 2, 0.15);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                0.7F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    public static boolean isSlamming(ServerPlayer player) {
        return SLAMS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tickGrab(player);
        if (isBlocking(player)) {
            if (!isOn(player) || !player.isAlive() || isStunned(player)) {
                BLOCKING.remove(player.getUUID());
            } else if (player.tickCount % 4 == 0) {
                guard(player.serverLevel(), player, false);
            }
        }
        if (player.onGround() && AIR_SAFE.containsKey(player.getUUID())
                && player.level().getGameTime() > AIR_SAFE.get(player.getUUID()) - 95) {
            AIR_SAFE.remove(player.getUUID());
        }
        Slam slam = SLAMS.get(player.getUUID());
        if (slam == null) {
            return;
        }
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            land(player);
        } else if (player.level().getGameTime() - slam.started() > SLAM_TIMEOUT || !player.isAlive()) {
            SLAMS.remove(player.getUUID());
        } else {
            // Keep diving (the client controls its own movement; keep pushing it down).
            player.setDeltaMovement(player.getDeltaMovement().x * 0.5, Math.min(player.getDeltaMovement().y, -2.0),
                    player.getDeltaMovement().z * 0.5);
            player.hurtMarked = true;
            player.resetFallDistance();
        }
    }

    /** No fall damage from your own slam. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AIR_SAFE.containsKey(player.getUUID())
                && !SLAMS.containsKey(player.getUUID())) {
            // Riding an uppercut or fighting in mid-air.
            if (player.level().getGameTime() <= AIR_SAFE.remove(player.getUUID())) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getEntity() instanceof ServerPlayer player && (SLAMS.containsKey(player.getUUID())
                || player.getPersistentData().getLong(LANDED_KEY) >= player.level().getGameTime() - 10)) {
            land(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("combat").executes(context -> {
            toggle(context.getSource().getPlayerOrException());
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, 0);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        COMBOS.remove(event.getEntity().getUUID());
        SLAMS.remove(event.getEntity().getUUID());
        UPPERCUT_ASKED.remove(event.getEntity().getUUID());
        UPPERCUTTING.remove(event.getEntity().getUUID());
        FINISHING.remove(event.getEntity().getUUID());
        BLOCKING.remove(event.getEntity().getUUID());
        GRABS.remove(event.getEntity().getUUID());
        GRABS.values().removeIf(grab -> grab.target() == event.getEntity());
        DASHED.remove(event.getEntity().getUUID());
        AIR_SAFE.remove(event.getEntity().getUUID());
    }

    // ---- Blocking ----

    public static void setBlocking(ServerPlayer player, boolean on) {
        if (!on) {
            BLOCKING.remove(player.getUUID());
            return;
        }
        if (!isOn(player) || isStunned(player) || GRABS.containsKey(player.getUUID()) || isBlocking(player)) {
            return;
        }
        BLOCKING.put(player.getUUID(), player.level().getGameTime());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                SoundSource.PLAYERS, 0.6F, 1.4F);
        guard(player.serverLevel(), player, false);
    }

    public static boolean isBlocking(ServerPlayer player) {
        return BLOCKING.containsKey(player.getUUID());
    }

    /** Whether the hit comes from in front of the blocker. */
    static boolean blocksFrom(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        Vec3 toward = from.subtract(player.position()).multiply(1, 0, 1);
        Vec3 facing = Vec3.directionFromRotation(0, player.getYRot());
        return toward.lengthSqr() < 1.0E-4 || toward.normalize().dot(facing) > 0;
    }

    /** A hit lands on a block: a parry right as the block goes up, a guard break from a finisher, or just less damage. */
    private static void block(LivingIncomingDamageEvent event, ServerPlayer blocker, Entity attacker) {
        ServerLevel level = blocker.serverLevel();
        long since = level.getGameTime() - BLOCKING.get(blocker.getUUID());
        if (since <= Config.COMBAT_PARRY_TICKS.get()) {
            event.setCanceled(true);
            if (attacker instanceof LivingEntity living && !Allies.isFriendly(living, blocker)) {
                stun(living, Config.COMBAT_STUN_TICKS.get() + 15);
            }
            guard(level, blocker, true);
            level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                    1.0F, 1.6F);
            level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 1.4F);
            blocker.displayClientMessage(Component.translatable("tensurafragments.combat.parry").withStyle(ChatFormatting.GOLD),
                    true);
            return;
        }
        if (attacker instanceof ServerPlayer player && FINISHING.containsKey(player.getUUID())) {
            // A finisher breaks the guard: full damage, and the blocker's stunned.
            BLOCKING.remove(blocker.getUUID());
            stun(blocker, Config.COMBAT_STUN_TICKS.get() + 13);
            level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS,
                    1.0F, 1.0F);
            blocker.displayClientMessage(Component.translatable("tensurafragments.combat.guard_break")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        event.setAmount((float) (event.getAmount() * (1 - Config.COMBAT_BLOCK_REDUCTION.get())));
        level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                0.8F, 1.1F);
        guard(level, blocker, false);
    }

    /** A white arc in front of a blocking player (a burst of them for a parry). */
    static void guard(ServerLevel level, ServerPlayer player, boolean parry) {
        Vec3 centre = player.getEyePosition().subtract(0, 0.5, 0);
        float yaw = player.getYRot();
        int points = parry ? 16 : 7;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            double angle = Math.toRadians(yaw + 60 - 120 * t);
            double radius = parry ? 1.1 : 0.8;
            Vec3 at = centre.add(-Math.sin(angle) * radius, Math.sin(t * Math.PI) * 0.3, Math.cos(angle) * radius);
            level.sendParticles(parry ? WHITE_BIG : WHITE, at.x, at.y, at.z, parry ? 2 : 1, 0, parry ? 0.4 : 0, 0,
                    parry ? 0.02 : 0);
        }
    }

    // ---- Grabs ----

    /** The grab key: grab what you're looking at, or throw what you're already holding. */
    public static boolean grabOrThrow(ServerPlayer player) {
        if (GRABS.containsKey(player.getUUID())) {
            throwHeld(player);
            return true;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 reach = player.getViewVector(1F).scale(3.5);
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(), player, eye, eye.add(reach), player.getBoundingBox().expandTowards(reach).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !Allies.isFriendly(e, player));
        return hit != null && grab(player, (LivingEntity) hit.getEntity());
    }

    /** Takes hold of a creature (or player) in front of you; a grab goes straight through a block. */
    public static boolean grab(ServerPlayer player, LivingEntity target) {
        if (!isOn(player) || isStunned(player) || GRABS.containsKey(player.getUUID()) || target == player
                || !target.isAlive() || Allies.isFriendly(target, player) || isHeld(target)
                || target.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)
                || target.getBbWidth() > 2.5F || target.distanceTo(player) > 4.5
                || target instanceof ServerPlayer dodger && isDodging(dodger)) {
            return false;
        }
        BLOCKING.remove(player.getUUID());
        if (target instanceof ServerPlayer blocker) {
            BLOCKING.remove(blocker.getUUID());
            GRABS.remove(blocker.getUUID());
        }
        GRABS.put(player.getUUID(), new Grab(target, player.level().getGameTime()));
        stun(target, GRAB_TICKS + 5);
        ServerLevel level = player.serverLevel();
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS,
                1.0F, 0.8F);
        hitSpark(level, target);
        hold(player, target);
        return true;
    }

    public static boolean isGrabbing(ServerPlayer player) {
        return GRABS.containsKey(player.getUUID());
    }

    public static boolean isHeld(Entity entity) {
        for (Grab grab : GRABS.values()) {
            if (grab.target() == entity) {
                return true;
            }
        }
        return false;
    }

    /** Held up in front of you. */
    private static void hold(ServerPlayer player, LivingEntity target) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 at = player.position().add(forward.scale(0.6 + (player.getBbWidth() + target.getBbWidth()) / 2)).add(0, 0.4, 0);
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
        target.teleportTo(at.x, at.y, at.z);
    }

    private static void tickGrab(ServerPlayer player) {
        Grab grab = GRABS.get(player.getUUID());
        if (grab == null) {
            return;
        }
        LivingEntity target = grab.target();
        if (!player.isAlive() || !isOn(player) || !target.isAlive() || target.isRemoved() || target.level() != player.level()
                || target.distanceTo(player) > 8 || player.level().getGameTime() - grab.started() > GRAB_TICKS) {
            GRABS.remove(player.getUUID());
            return;
        }
        hold(player, target);
    }

    /** Throws what you're holding: forward and up, hard. */
    public static void throwHeld(ServerPlayer player) {
        Grab grab = GRABS.remove(player.getUUID());
        if (grab == null || !grab.target().isAlive()) {
            return;
        }
        LivingEntity target = grab.target();
        ServerLevel level = player.serverLevel();
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), Config.COMBAT_THROW_DAMAGE.get().floatValue());
        // After the hurt, so its knockback doesn't eat the throw.
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        double resist = Math.max(0.3,
                1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        target.setDeltaMovement(forward.x * 1.5 * resist, 0.5 * resist, forward.z * 1.5 * resist);
        target.hurtMarked = true;
        stun(target, Config.COMBAT_STUN_TICKS.get() + 10);
        swish(level, player, true, true);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS,
                0.7F, 1.6F);
    }

    // ---- Dash ----

    /**
     * Dash: the client moves you (it owns its own movement); here you get the moment of invulnerability, the trail and
     * the sound. The direction is the way you were moving.
     */
    public static boolean dash(ServerPlayer player, Vec3 direction) {
        long now = player.level().getGameTime();
        Long last = DASHED.get(player.getUUID());
        if (!isOn(player) || isStunned(player) || GRABS.containsKey(player.getUUID())
                || last != null && now - last < Config.COMBAT_DASH_COOLDOWN.get()) {
            return false;
        }
        DASHED.put(player.getUUID(), now);
        BLOCKING.remove(player.getUUID());
        player.resetFallDistance();
        Vec3 dir = direction.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : direction.normalize();
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 10; i++) {
            Vec3 at = player.position().add(dir.scale(-i * 0.35)).add(0, 0.2 + (i % 3) * 0.35, 0);
            level.sendParticles(WHITE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 5, 0.2, 0.05, 0.2, 0.03);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.7F, 1.7F);
        return true;
    }

    /** In a dash's invulnerable moment. */
    public static boolean isDodging(ServerPlayer player) {
        Long last = DASHED.get(player.getUUID());
        return last != null && player.level().getGameTime() - last <= Config.COMBAT_DASH_IFRAMES.get();
    }
}
