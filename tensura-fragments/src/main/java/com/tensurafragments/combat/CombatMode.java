package com.tensurafragments.combat;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.ally.Allies;
import com.tensurafragments.network.CombatPosePayload;
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
 * Combat Mode, toggled per player: melee hits chain into combos (left and right hooks, a little lunge),
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

    private record Slam(double fromY, long started, double power) {
    }

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();
    private static final Map<UUID, Slam> SLAMS = new HashMap<>();
    /** The finisher being dealt right now (for its damage and knockback). */
    private static final Map<UUID, Boolean> FINISHING = new HashMap<>();
    /** When the client said the next punch is an uppercut (jumping while punching). */
    private static final Map<UUID, Long> UPPERCUT_ASKED = new HashMap<>();
    /** The launcher (a jumping punch: uppercut, spin kick, hammer fist or ki palm) being dealt right now. */
    private static final Map<UUID, Boolean> LAUNCHING = new HashMap<>();
    /** Players blocking, and when they started (for parries). */
    static final Map<UUID, Long> BLOCKING = new HashMap<>();
    /** What each player is holding, and since when. */
    static final Map<UUID, Grab> GRABS = new HashMap<>();
    /** When each player last dashed. */
    static final Map<UUID, Long> DASHED = new HashMap<>();
    /** Players riding an uppercut up: no fall damage until they land (or this time passes). */
    static final Map<UUID, Long> AIR_SAFE = new HashMap<>();
    /** How long a grab holds before letting go. */
    private static final int GRAB_TICKS = 40;

    record Grab(LivingEntity target, long started) {
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
        applyAttackSpeed(player);
        sync(player, 0);
    }

    public static void sync(ServerPlayer player, int combo) {
        sync(player, combo, 0);
    }

    /** Combat Mode, the combo count, the style and the down power of what you last hit, for your HUD. */
    public static void sync(ServerPlayer player, int combo, int down) {
        PacketDistributor.sendToPlayer(player, new SyncCombatPayload(isOn(player), combo, style(player).ordinal(), down));
    }

    // ---- Fighting styles ----

    public static FightingStyle style(ServerPlayer player) {
        return FightingStyle.byId(player.getData(ModRegistries.COMBAT_STYLE));
    }

    public static void setStyle(ServerPlayer player, FightingStyle style) {
        player.setData(ModRegistries.COMBAT_STYLE, style.ordinal());
        COMBOS.remove(player.getUUID());
        BLOCKING.remove(player.getUUID());
        GRABS.remove(player.getUUID());
        StyleMoves.clear(player);
        player.displayClientMessage(Component.translatable("tensurafragments.combat.style_set",
                Component.translatable(style.translationKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(style.translationKey() + ".moves")), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                SoundSource.PLAYERS, 0.8F, 1.2F);
        applyAttackSpeed(player);
        sync(player, 0);
    }

    public static void cycleStyle(ServerPlayer player) {
        if (Config.COMBAT_ENABLED.get()) {
            setStyle(player, style(player).next());
        }
    }

    private static final net.minecraft.resources.ResourceLocation STYLE_SPEED = TensuraFragments.id("combat_style_speed");

    /** Swift punches faster, Titan slower (only while Combat Mode is on). */
    static void applyAttackSpeed(ServerPlayer player) {
        var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        double amount = isOn(player) ? style(player).attackSpeed() : 0;
        if (amount == 0) {
            attribute.removeModifier(STYLE_SPEED);
        } else {
            attribute.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(STYLE_SPEED,
                    amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ---- Down power ----

    private static final String DOWN_KEY = "tensurafragments_down_power";
    private static final String DOWN_HIT_KEY = "tensurafragments_down_hit";
    private static final String DOWNED_KEY = "tensurafragments_downed_until";

    /** The target's down power (0 to 100), emptied once it hasn't been hit for a while. */
    public static float downPower(LivingEntity target) {
        long since = target.level().getGameTime() - target.getPersistentData().getLong(DOWN_HIT_KEY);
        return since > Config.COMBAT_DOWN_RESET_TICKS.get() ? 0 : target.getPersistentData().getFloat(DOWN_KEY);
    }

    /** Knocked down: for a moment it can't be stunned, juggled or grabbed. */
    public static boolean isDowned(Entity entity) {
        return entity.getPersistentData().getLong(DOWNED_KEY) > entity.level().getGameTime();
    }

    /** Adds down power to what was hit; a full gauge (100) knocks it down. Returns the gauge afterwards. */
    public static float addDown(LivingEntity target, float amount, @org.jetbrains.annotations.Nullable Entity by) {
        if (isDowned(target) || amount <= 0) {
            return isDowned(target) ? 100 : downPower(target);
        }
        float down = downPower(target) + amount;
        target.getPersistentData().putLong(DOWN_HIT_KEY, target.level().getGameTime());
        if (down >= 100) {
            knockDown(target, by);
            return 100;
        }
        target.getPersistentData().putFloat(DOWN_KEY, down);
        return down;
    }

    /** Down: the gauge empties, the stun ends, it drops out of the air and gets a moment to recover. */
    public static void knockDown(LivingEntity target, @org.jetbrains.annotations.Nullable Entity by) {
        ServerLevel level = (ServerLevel) target.level();
        target.getPersistentData().putFloat(DOWN_KEY, 0);
        target.getPersistentData().putLong(DOWNED_KEY, level.getGameTime() + Config.COMBAT_DOWN_TICKS.get());
        target.getPersistentData().remove(STUN_KEY);
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        GRABS.values().removeIf(grab -> grab.target() == target);
        Vec3 motion = target.getDeltaMovement();
        if (!target.onGround()) {
            // Slammed down out of a juggle.
            target.setDeltaMovement(motion.x * 0.3, Math.min(motion.y, -0.9), motion.z * 0.3);
        } else if (by != null) {
            Vec3 away = target.position().subtract(by.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.6);
            target.setDeltaMovement(away.x, 0.3, away.z);
        }
        target.hurtMarked = true;
        com.tensurafragments.combatanim.CombatAnim.stopEffect(target, "fx_stun_mark");
        if (!(target instanceof ServerPlayer)) {
            // A knocked-down player's knocked_down animation shows the slam itself.
            com.tensurafragments.combatanim.CombatAnim.effect(target, "fx_knockdown", null);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS,
                1.0F, 0.6F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("tensurafragments.combat.knocked_down")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    // ---- Strikes ----

    /** Down power and stun of the strike being dealt right now (-1: an ordinary punch). */
    private static float pendingDown = -1;
    private static int pendingStun = -1;

    /**
     * A special move's hit: dealt as your attack (so it counts as a Combat Mode hit and scales with your EP), with its
     * own down power and stun instead of a punch's.
     */
    static boolean strike(ServerPlayer player, LivingEntity target, float damage, float down, int stunTicks) {
        if (target == player || !target.isAlive() || Allies.isFriendly(target, player)) {
            return false;
        }
        float lastDown = pendingDown;
        int lastStun = pendingStun;
        pendingDown = down;
        pendingStun = stunTicks;
        try {
            target.invulnerableTime = 0;
            return target.hurt(player.damageSources().playerAttack(player), damage);
        } finally {
            pendingDown = lastDown;
            pendingStun = lastStun;
        }
    }

    /** Your punch's base damage (your attack damage). */
    static float punchDamage(ServerPlayer player) {
        return (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
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

    public static boolean stun(LivingEntity target, int ticks) {
        if (ticks <= 0 || isDowned(target) || target instanceof ServerPlayer player && StyleMoves.isArmored(player)) {
            return false;
        }
        target.getPersistentData().putLong(STUN_KEY, target.level().getGameTime() + ticks);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, false));
        // The stars over its head, for as long as the stun lasts (a fresh stun replaces the last one's).
        com.tensurafragments.combatanim.CombatAnim.stopEffect(target, "fx_stun_mark");
        com.tensurafragments.combatanim.CombatAnim.effect(target, "fx_stun_mark", Float.NaN, ticks);
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return true;
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
        if (event.getEntity() instanceof ServerPlayer target && attacker instanceof LivingEntity living
                && StyleMoves.counter(target, living)) {
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof ServerPlayer player && isOn(player) && isMelee(player, event.getSource())) {
            // An ordinary punch hits as hard as the style does (special moves set their own damage).
            if (pendingDown < 0) {
                event.setAmount(event.getAmount() * style(player).damage());
            }
            // Something knocked down takes less from Combat Mode hits.
            if (isDowned(event.getEntity())) {
                event.setAmount(event.getAmount() * 0.5F);
            }
        }
        if (event.getEntity() instanceof ServerPlayer target && StyleMoves.isIronBody(target)) {
            event.setAmount(event.getAmount() * 0.6F);
        }
        // The finisher hits harder.
        if (attacker instanceof ServerPlayer player && FINISHING.containsKey(player.getUUID())) {
            event.setAmount((float) (event.getAmount() * Config.COMBAT_FINISHER_DAMAGE.get() + 3));
        }
        if (attacker instanceof ServerPlayer player && LAUNCHING.containsKey(player.getUUID())) {
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
                || Allies.isFriendly(event.getTarget(), player) || LANDING.contains(player.getUUID())) {
            return;
        }
        if (GRABS.containsKey(player.getUUID())) {
            // Punching while holding something throws it.
            event.setCanceled(true);
            throwHeld(player);
            return;
        }
        FightingStyle style = style(player);
        int count = combo(player) + 1;
        int finisher = style.finisherHit();
        COMBOS.put(player.getUUID(), new Combo(count >= finisher ? 0 : count, player.level().getGameTime()));
        if (count >= finisher) {
            FINISHING.put(player.getUUID(), true);
        } else {
            FINISHING.remove(player.getUUID());
        }
        Long asked = UPPERCUT_ASKED.remove(player.getUUID());
        boolean uppercut = asked != null && player.level().getGameTime() - asked <= 5;
        if (uppercut) {
            LAUNCHING.put(player.getUUID(), true);
            AIR_SAFE.put(player.getUUID(), player.level().getGameTime() + 100);
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            if (style == FightingStyle.BRAWLER) {
                // You go up with it when the fist lands (in landPunch), a touch slower so it stays just above you.
                // Everyone watching sees the uppercut (the puncher's own client already started it).
                PacketDistributor.sendToPlayersTrackingEntity(player,
                        new com.tensurafragments.combatanim.CombatAnimPayloads.PlayS2C(player.getId(), UPPERCUT_ANIMATION));
            } else {
                // The other styles' jumping moves keep you hanging in the air a moment.
                player.setDeltaMovement(forward.x * 0.1, AIR_HIT_LIFT, forward.z * 0.1);
                animate(player, switch (style) {
                    case SWIFT -> "swift_spin_kick";
                    case TITAN -> "titan_hammer_fist";
                    default -> "ki_palm";
                });
            }
            player.hurtMarked = true;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    SoundSource.PLAYERS, 1.1F, style == FightingStyle.TITAN ? 0.5F : 0.8F);
        } else {
            LAUNCHING.remove(player.getUUID());
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
            if (count >= finisher) {
                animate(player, switch (style) {
                    case BRAWLER -> "brawler_finisher";
                    case SWIFT -> "swift_whirlwind";
                    case TITAN -> "titan_ground_pound";
                    case KI -> "ki_blast";
                });
            }
        }
        int shown = count >= finisher ? finisher : count;
        LAST_COMBO.put(player.getUUID(), shown);
        sync(player, shown);
        // The hit lands when the fist (or foot) does in the animation.
        int delay = hitDelay(player, style, uppercut, count >= finisher);
        if (delay > 0) {
            event.setCanceled(true);
            LivingEntity target = (LivingEntity) event.getTarget();
            float strength = player.getAttackStrengthScale(0.5F);
            boolean finishing = count >= finisher;
            double lift = uppercut && style == FightingStyle.BRAWLER ? uppercutPower(finishing) * 0.95 : -1;
            later(player, delay, () -> landPunch(player, target, strength, finishing, uppercut, lift));
        }
    }

    // ---- Hits that land with the animation ----

    private record Delayed(ServerPlayer player, int due, Runnable action) {
    }

    private static final java.util.List<Delayed> DELAYED = new java.util.ArrayList<>();
    /** Players whose held-back punch is landing right now (so it isn't held back again). */
    private static final java.util.Set<UUID> LANDING = new java.util.HashSet<>();

    /** Does this for the player after so many ticks (now, for 0), unless they've left or died by then. */
    static void later(ServerPlayer player, int ticks, Runnable action) {
        if (ticks <= 0) {
            action.run();
        } else {
            DELAYED.add(new Delayed(player, player.server.getTickCount() + ticks, action));
        }
    }

    /** Lands everything this player has held back right now, in order (for tests). */
    public static void landNow(ServerPlayer player) {
        java.util.List<Delayed> mine = new java.util.ArrayList<>();
        DELAYED.removeIf(d -> d.player() == player && mine.add(d));
        mine.forEach(d -> d.action().run());
    }

    /** Whether this player has a hit or move effect still waiting for its animation (for tests). */
    public static boolean hasPending(ServerPlayer player) {
        return DELAYED.stream().anyMatch(d -> d.player() == player);
    }

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (DELAYED.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        java.util.List<Delayed> due = new java.util.ArrayList<>();
        DELAYED.removeIf(d -> {
            if (d.player().isRemoved() || !d.player().isAlive()) {
                return true;
            }
            if (d.due() <= now) {
                due.add(d);
                return true;
            }
            return false;
        });
        due.forEach(d -> d.action().run());
    }

    /**
     * Ticks from the click to the moment the punch connects in its animation (the kit's strike time, or where the
     * striking limb's swing ends); 0 when no animation plays (a weapon swing).
     */
    static int hitDelay(ServerPlayer player, FightingStyle style, boolean uppercut, boolean finisher) {
        if (uppercut) {
            return switch (style) {
                case BRAWLER -> 5;   // punch_uppercut_left, strike 0.25 s
                case SWIFT -> 8;     // swift_spin_kick, the kick comes round at 0.4 s
                case TITAN -> 7;     // titan_hammer_fist, 0.35 s
                case KI -> 4;        // ki_palm, 0.2 s
            };
        }
        if (finisher) {
            return switch (style) {
                case BRAWLER -> 6;   // brawler_finisher, 0.3 s
                case SWIFT -> 9;     // swift_whirlwind, facing front again at 0.45 s
                case TITAN -> 10;    // titan_ground_pound, the shockwave at 0.5 s
                case KI -> 10;       // ki_blast, 0.5 s
            };
        }
        // A hook (punch_hook_right/left, strike 0.22 s), when bare-handed in the guard.
        return !player.isCrouching() && !player.getMainHandItem().isDamageableItem() ? 4 : 0;
    }

    /** The held-back punch lands, with the charge it was thrown with. */
    private static void landPunch(ServerPlayer player, LivingEntity target, float strength, boolean finishing,
            boolean launching, double lift) {
        if (!target.isAlive() || target.isRemoved() || target.level() != player.level() || player.distanceTo(target) > 6) {
            return;
        }
        if (finishing) {
            FINISHING.put(player.getUUID(), true);
        } else {
            FINISHING.remove(player.getUUID());
        }
        if (launching) {
            LAUNCHING.put(player.getUUID(), true);
        } else {
            LAUNCHING.remove(player.getUUID());
        }
        if (lift >= 0) {
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            player.setDeltaMovement(forward.x * 0.1, lift, forward.z * 0.1);
            player.hurtMarked = true;
        }
        com.tensurafragments.mixin.LivingEntityAccessor charge = (com.tensurafragments.mixin.LivingEntityAccessor) player;
        int saved = charge.tensurafragments$getAttackStrengthTicker();
        float full = player.getCurrentItemAttackStrengthDelay();
        charge.tensurafragments$setAttackStrengthTicker(strength >= 1 ? (int) full * 2 + 1
                : (int) Math.ceil(strength * full - 0.5F));
        LANDING.add(player.getUUID());
        try {
            player.attack(target);
        } finally {
            LANDING.remove(player.getUUID());
            // The next punch charges from when it was thrown, as usual.
            charge.tensurafragments$setAttackStrengthTicker(saved);
        }
    }

    /** The combo count last shown to each player (sent again with the down power once the hit lands). */
    private static final Map<UUID, Integer> LAST_COMBO = new HashMap<>();

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
            // A held block isn't stunned (a broken one already was); its block_hit animation shows the ripple.
            return;
        }
        FightingStyle style = style(player);
        if (pendingDown >= 0) {
            // A special move's strike: its own stun and down power.
            if (stun(target, pendingStun) && target instanceof ServerPlayer hit) {
                animate(hit, "hit_stun");
            }
            sync(player, LAST_COMBO.getOrDefault(player.getUUID(), 0), (int) addDown(target, pendingDown, player));
            hitSpark(target, player);
            return;
        }
        float down = style.downPerHit() + (finisher ? 25 : 0) + (LAUNCHING.containsKey(player.getUUID()) ? 12 : 0);
        sync(player, LAST_COMBO.getOrDefault(player.getUUID(), 0), (int) addDown(target, down, player));
        if (finisher) {
            // The launch itself comes with the knockback, just after this.
            com.tensurafragments.combatanim.CombatAnim.effect(target, "fx_finisher", player);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.2F, 0.7F);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.4F, 1.8F);
            stun(target, Config.COMBAT_STUN_TICKS.get() + 10);
        } else {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.8F, 1.3F);
            if (stun(target, Config.COMBAT_STUN_TICKS.get()) && target instanceof ServerPlayer hit) {
                animate(hit, "hit_stun");
            }
            target.invulnerableTime = 0;
        }
        if (!finisher) {
            hitSpark(target, player);
        }
    }

    /** Combo hits keep the target close; the finisher launches it up and away instead. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof ServerPlayer blocker && isBlocking(blocker)) {
            event.setStrength(event.getStrength() * 0.3F);
            return;
        }
        if (isHeld(target) || target instanceof ServerPlayer armored && StyleMoves.isArmored(armored)) {
            event.setCanceled(true);
            return;
        }
        if (!(target.getLastHurtByMob() instanceof ServerPlayer player) || !isOn(player)
                || target.getLastHurtByMobTimestamp() != target.tickCount) {
            return;
        }
        boolean finisher = FINISHING.remove(player.getUUID()) != null;
        FightingStyle style = style(player);
        if (isDowned(target)) {
            // Knocked down: it just goes down, no launches.
            event.setStrength(event.getStrength() * 0.5F);
            LAUNCHING.remove(player.getUUID());
        } else if (LAUNCHING.remove(player.getUUID()) != null) {
            event.setCanceled(true);
            if (style == FightingStyle.BRAWLER) {
                uppercutLaunch(target, uppercutPower(finisher));
                stun(target, Config.COMBAT_STUN_TICKS.get() + 8);
            } else {
                StyleMoves.launcher(player, target, style, finisher);
            }
        } else if (finisher) {
            event.setCanceled(true);
            if (style == FightingStyle.BRAWLER) {
                launch(player, target);
            } else {
                StyleMoves.finisher(player, target, style);
            }
        } else {
            event.setStrength(event.getStrength() * style.knockback());
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


    /** A solid white pixel arc through these points, drawn by everyone nearby. */
    static void arc(ServerLevel level, java.util.List<Vec3> points, float thickness, int life, boolean sweep) {
        Vec3 at = points.get(0);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 64,
                new com.tensurafragments.network.ArcPayload(points, thickness, life, sweep));
    }

    static void launch(ServerPlayer player, LivingEntity target) {
        Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : away.normalize();
        double resist = 1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        target.setDeltaMovement(away.x * 1.4 * resist, 0.75 * Math.max(0.3, resist), away.z * 1.4 * resist);
        target.hurtMarked = true;
    }


    /** The kit's hit spark on what was hit, facing whoever hit it. */
    static void hitSpark(LivingEntity target, Entity attacker) {
        com.tensurafragments.combatanim.CombatAnim.effect(target, "fx_hit_spark", attacker);
    }

    // ---- Down slam ----

    /** Sneak and attack in mid-air: the style's air move (Brawler and Titan slam, Swift dive kicks, Ki bombs). */
    public static boolean airSpecial(ServerPlayer player) {
        if (!isOn(player) || isStunned(player)) {
            return false;
        }
        return switch (style(player)) {
            case BRAWLER, TITAN -> slam(player);
            case SWIFT -> StyleMoves.diveKick(player);
            case KI -> StyleMoves.kiBomb(player);
        };
    }

    /** Dive straight down (the Titan's meteor slam lands bigger). */
    public static boolean slam(ServerPlayer player) {
        if (!isOn(player) || player.onGround() || player.isInWater() || SLAMS.containsKey(player.getUUID())) {
            return false;
        }
        SLAMS.put(player.getUUID(), new Slam(player.getY(), player.level().getGameTime(),
                style(player) == FightingStyle.TITAN ? 1.5 : 1.0));
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
        animate(player, slam.power() > 1 ? "titan_meteor_land" : "brawler_slam_land");
        double height = Math.max(0, slam.fromY() - player.getY());
        double radius = Config.COMBAT_SLAM_RADIUS.get() * slam.power();
        float damage = (float) ((Config.COMBAT_SLAM_DAMAGE.get() + Math.min(height, 30) * 0.4) * slam.power());
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1.5, radius),
                e -> e != player && e.isAlive() && !Allies.isFriendly(e, player))) {
            double distance = target.distanceTo(player);
            if (distance > radius + 0.5) {
                continue;
            }
            float falloff = (float) Math.max(0.35, 1 - distance / (radius + 1));
            strike(player, target, damage * falloff, 20, Config.COMBAT_STUN_TICKS.get());
            Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.6 * falloff);
            target.setDeltaMovement(away.x, 0.65 * falloff + 0.2, away.z);
            target.hurtMarked = true;
        }
        // Dust and a thud (the landing animation draws the shockwave on the ground).
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
        sendPose(player);
        StyleMoves.tick(player);
        if (player.tickCount % 20 == 0) {
            applyAttackSpeed(player);
        }
        if (isBlocking(player)) {
            // The guard shield shows with the block animation.
            if (!isOn(player) || !player.isAlive() || isStunned(player)) {
                BLOCKING.remove(player.getUUID());
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
        }).then(net.minecraft.commands.Commands.literal("style").then(net.minecraft.commands.Commands
                .argument("style", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        java.util.Arrays.stream(FightingStyle.values()).map(FightingStyle::id), builder))
                .executes(context -> {
                    FightingStyle style = FightingStyle.byName(
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "style"));
                    if (style == null) {
                        context.getSource().sendFailure(Component.translatable("tensurafragments.combat.no_style"));
                        return 0;
                    }
                    setStyle(context.getSource().getPlayerOrException(), style);
                    return 1;
                }))));
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
        LAUNCHING.remove(event.getEntity().getUUID());
        FINISHING.remove(event.getEntity().getUUID());
        THROWING.remove(event.getEntity().getUUID());
        DELAYED.removeIf(d -> d.player() == event.getEntity());
        BLOCKING.remove(event.getEntity().getUUID());
        GRABS.remove(event.getEntity().getUUID());
        GRABS.values().removeIf(grab -> grab.target() == event.getEntity());
        DASHED.remove(event.getEntity().getUUID());
        AIR_SAFE.remove(event.getEntity().getUUID());
        SHOWN_POSE.remove(event.getEntity().getUUID());
        LAST_COMBO.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            StyleMoves.clear(player);
        }
    }

    // ---- Stances (for drawing the arms) ----

    /** The stance each player was last shown in. */
    private static final Map<UUID, Integer> SHOWN_POSE = new HashMap<>();

    public static int pose(ServerPlayer player) {
        boolean airborne = !player.onGround() && !player.isInWater();
        if (isHeld(player)) {
            return CombatPosePayload.HELD;
        } else if (isDowned(player)) {
            return CombatPosePayload.DOWN;
        } else if (airborne && player.getPersistentData().getLong(THROWN_KEY) > player.level().getGameTime()) {
            return CombatPosePayload.THROWN;
        } else if (airborne && isStunned(player)) {
            return CombatPosePayload.JUGGLE;
        } else if (isGrabbing(player)) {
            return CombatPosePayload.GRAB;
        } else if (isBlocking(player)) {
            return CombatPosePayload.BLOCK;
        } else if (StyleMoves.isTackling(player)) {
            return CombatPosePayload.TACKLE;
        } else if (StyleMoves.isDiving(player)) {
            return CombatPosePayload.DIVE_KICK;
        } else if (isSlamming(player)) {
            return SLAMS.get(player.getUUID()).power() > 1 ? CombatPosePayload.METEOR_DIVE : CombatPosePayload.SLAM_DIVE;
        } else if (StyleMoves.isIronBody(player)) {
            return CombatPosePayload.IRON_BODY;
        } else if (StyleMoves.isCountering(player)) {
            return CombatPosePayload.COUNTER;
        }
        return isOn(player) ? CombatPosePayload.STANCE : CombatPosePayload.NONE;
    }

    /** Thrown players tumble through the air until this time. */
    static final String THROWN_KEY = "tensurafragments_thrown_until";

    /** The Brawler's jumping punch. */
    public static final String UPPERCUT_ANIMATION = "punch_uppercut_left";

    /** A one-off animation on this player, for them and everyone watching. */
    static void animate(ServerPlayer player, String animation) {
        com.tensurafragments.combatanim.CombatAnim.play(player, animation);
    }

    /** Tells the player and everyone watching them when their stance changes. */
    private static void sendPose(ServerPlayer player) {
        int pose = pose(player);
        Integer shown = SHOWN_POSE.get(player.getUUID());
        if (shown == null ? pose != CombatPosePayload.NONE : shown != pose) {
            SHOWN_POSE.put(player.getUUID(), pose);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CombatPosePayload(player.getId(), pose));
        }
    }

    /** Someone coming into view already blocking or grabbing. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher
                && pose(target) != CombatPosePayload.NONE) {
            PacketDistributor.sendToPlayer(watcher, new CombatPosePayload(target.getId(), pose(target)));
        }
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
            animate(blocker, "parry");
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
            animate(blocker, "guard_break");
            level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS,
                    1.0F, 1.0F);
            blocker.displayClientMessage(Component.translatable("tensurafragments.combat.guard_break")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        event.setAmount((float) (event.getAmount() * (1 - Config.COMBAT_BLOCK_REDUCTION.get())));
        animate(blocker, "block_hit");
        level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                0.8F, 1.1F);
    }

    /** A white arc in front of the player (Swift's counter stance; the block, parry and guard break have the kit's). */
    static void guard(ServerLevel level, ServerPlayer player, boolean parry) {
        Vec3 centre = player.getEyePosition().subtract(0, 0.5, 0);
        float yaw = player.getYRot();
        int count = 20;
        java.util.List<Vec3> points = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = i / (double) (count - 1);
            double angle = Math.toRadians(yaw + 60 - 120 * t);
            double radius = parry ? 1.1 : 0.8;
            points.add(centre.add(-Math.sin(angle) * radius, Math.sin(t * Math.PI) * 0.3, Math.cos(angle) * radius));
        }
        // Held up while blocking (sent again every few ticks); a parry is a bigger flash.
        arc(level, points, parry ? 0.18F : 0.07F, parry ? 8 : 5, false);
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
                || !target.isAlive() || Allies.isFriendly(target, player) || isHeld(target) || isDowned(target)
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
        animate(player, "brawler_grab");
        stun(target, GRAB_TICKS + 5);
        ServerLevel level = player.serverLevel();
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS,
                1.0F, 0.8F);
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
    /** Ticks into brawler_throw when it lets go (where the throwing arm's swing ends, 0.3 s). */
    static final int THROW_RELEASE_TICKS = 6;
    /** Players winding up a throw (still holding on until the release). */
    private static final java.util.Set<UUID> THROWING = new java.util.HashSet<>();

    public static void throwHeld(ServerPlayer player) {
        Grab grab = GRABS.get(player.getUUID());
        if (grab == null || !grab.target().isAlive() || !THROWING.add(player.getUUID())) {
            return;
        }
        animate(player, "brawler_throw");
        later(player, THROW_RELEASE_TICKS, () -> {
            THROWING.remove(player.getUUID());
            if (GRABS.get(player.getUUID()) == grab) {
                GRABS.remove(player.getUUID());
                release(player, grab.target());
            }
        });
    }

    private static void release(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        strike(player, target, Config.COMBAT_THROW_DAMAGE.get().floatValue(), 35, Config.COMBAT_STUN_TICKS.get() + 10);
        target.getPersistentData().putLong(THROWN_KEY, level.getGameTime() + 20);
        // After the hurt, so its knockback doesn't eat the throw.
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        double resist = Math.max(0.3,
                1 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        target.setDeltaMovement(forward.x * 1.5 * resist, 0.5 * resist, forward.z * 1.5 * resist);
        target.hurtMarked = true;
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
        boolean swift = style(player) == FightingStyle.SWIFT;
        // Swift's quick step: shorter, but ready again almost at once.
        int cooldown = swift ? 8 : Config.COMBAT_DASH_COOLDOWN.get();
        if (!isOn(player) || isStunned(player) || GRABS.containsKey(player.getUUID())
                || last != null && now - last < cooldown) {
            return false;
        }
        DASHED.put(player.getUUID(), now);
        BLOCKING.remove(player.getUUID());
        animate(player, swift ? "swift_quick_step" : "brawler_dash");
        player.resetFallDistance();
        // The dash trail comes with its animation.
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.7F, 1.7F);
        return true;
    }

    /** In a dash's invulnerable moment. */
    public static boolean isDodging(ServerPlayer player) {
        Long last = DASHED.get(player.getUUID());
        int frames = style(player) == FightingStyle.SWIFT ? Math.min(5, Config.COMBAT_DASH_IFRAMES.get())
                : Config.COMBAT_DASH_IFRAMES.get();
        return last != null && player.level().getGameTime() - last <= frames;
    }
}
