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
import net.minecraft.world.InteractionHand;
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
 * every hit stuns what it hits (it can't move or hurt anyone for a moment), the last hit of a combo is a finisher that
 * launches, and attacking while sneaking in mid-air slams you down into a shockwave.
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

    /** Stunned creatures stay put. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && isStunned(mob)) {
            mob.getNavigation().stop();
            Vec3 motion = mob.getDeltaMovement();
            mob.setDeltaMovement(motion.x * 0.5, Math.min(motion.y, mob.onGround() ? 0 : motion.y), motion.z * 0.5);
        }
    }

    /** ...and can't hurt anyone. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && event.getSource().getDirectEntity() == attacker && !attacker.level().isClientSide
                && isStunned(attacker)) {
            event.setCanceled(true);
            return;
        }
        // The finisher hits harder.
        if (attacker instanceof ServerPlayer player && FINISHING.containsKey(player.getUUID())) {
            event.setAmount((float) (event.getAmount() * Config.COMBAT_FINISHER_DAMAGE.get() + 3));
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
        int count = combo(player) + 1;
        int finisher = Config.COMBAT_FINISHER_HIT.get();
        COMBOS.put(player.getUUID(), new Combo(count >= finisher ? 0 : count, player.level().getGameTime()));
        if (count >= finisher) {
            FINISHING.put(player.getUUID(), true);
        } else {
            FINISHING.remove(player.getUUID());
        }
        // Left, right, left...: jabs from alternating hands (and a small step in).
        boolean left = count % 2 == 0;
        if (left && player.getMainHandItem().isEmpty()) {
            player.swing(InteractionHand.OFF_HAND, true);
        }
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        double lunge = count >= finisher ? 0.45 : 0.15;
        player.push(forward.x * lunge, 0, forward.z * lunge);
        player.hurtMarked = true;
        swish((ServerLevel) player.level(), player, left, count >= finisher);
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
        if (!(target.getLastHurtByMob() instanceof ServerPlayer player) || !isOn(player)
                || target.getLastHurtByMobTimestamp() != target.tickCount) {
            return;
        }
        if (FINISHING.remove(player.getUUID()) != null) {
            event.setCanceled(true);
            launch(player, target);
        } else {
            event.setStrength(event.getStrength() * 0.25F);
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
        FINISHING.remove(event.getEntity().getUUID());
    }
}
