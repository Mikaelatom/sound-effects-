package com.tensurafragments.yifa;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.network.SyncSpiritSightPayload;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBoltProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WindBladeProjectile;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Spirit Communion, after Yifa from <i>The Reincarnation of the Strongest Exorcist</i>: she has little magic of her own,
 * but her elf blood lets her see elemental spirits and command them, casting far beyond her own power. Spirits are
 * found in the world near their element; where you fight decides what you can cast. Triggered through
 * {@link SpiritCommunionSkill}; the Magisteel Spirit Bell and Lantern draw spirits to you.
 */
public final class SpiritCommunion {
    private static final Map<UUID, Long> JUTSU_READY = new HashMap<>();

    private SpiritCommunion() {
    }

    // ---- Spirit Sight --------------------------------------------------------------------------------------------

    public static boolean hasSight(ServerPlayer player) {
        return player.getData(ModRegistries.SPIRIT_SIGHT);
    }

    public static void toggleSight(ServerPlayer player) {
        setSight(player, !hasSight(player));
        player.displayClientMessage(Component.translatable(hasSight(player)
                ? "tensurafragments.yifa.sight_on" : "tensurafragments.yifa.sight_off"), true);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.8F, hasSight(player) ? 1.5F : 0.8F);
    }

    public static void setSight(ServerPlayer player, boolean on) {
        player.setData(ModRegistries.SPIRIT_SIGHT, on);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncSpiritSightPayload(hasSight(player),
                player.getData(ModRegistries.YIFA_JUTSU)));
    }

    // ---- Spirits in the world ------------------------------------------------------------------------------------

    /** Every second: Sight upkeep, wild spirits appearing near their element, and the lantern drawing them in. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || player.isSpectator()) {
            return;
        }
        boolean skill = hasSkill(player);
        boolean lantern = carriesLantern(player);
        if (hasSight(player) && (!skill || !Magicules.trySpend(player, Config.SPIRIT_SIGHT_MAGICULES_PER_SECOND.get()))) {
            setSight(player, false);
            if (skill) {
                player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            }
        }
        if (!skill && !lantern) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<WispEntity> wild = level.getEntities(ModRegistries.WISP.get(), player.getBoundingBox().inflate(24),
                w -> w.isAlive() && w.isWild());
        int cap = Config.YIFA_WILD_CAP.get() * (lantern ? 2 : 1);
        int attempts = lantern ? 2 : 1;
        for (int i = 0; i < attempts && wild.size() < cap; i++) {
            if (player.getRandom().nextFloat() < (lantern ? 0.7F : 0.35F)) {
                WispEntity wisp = trySpawnNear(level, player, 12);
                if (wisp != null) {
                    wild.add(wisp);
                }
            }
        }
        if (lantern) {
            for (WispEntity wisp : wild) {
                if (wisp.distanceTo(player) < 16) {
                    wisp.lure(player, false);
                }
            }
        }
    }

    /** Tries to call a wild spirit into being somewhere near the player, of whatever element is around there. */
    @Nullable
    static WispEntity trySpawnNear(ServerLevel level, ServerPlayer player, int radius) {
        RandomSource random = player.getRandom();
        for (int tries = 0; tries < 4; tries++) {
            BlockPos pos = player.blockPosition().offset(random.nextInt(radius * 2 + 1) - radius,
                    random.nextInt(7) - 2, random.nextInt(radius * 2 + 1) - radius);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                continue;
            }
            SpiritElement element = elementAt(level, pos, random);
            if (element != null) {
                WispEntity wisp = WispEntity.wild(level, element, Vec3.atCenterOf(pos));
                level.addFreshEntity(wisp);
                return wisp;
            }
        }
        return null;
    }

    /**
     * What kind of spirit gathers at {@code pos}: fire by flames, lava, torches and in the Nether; water by water;
     * wind high up and under open sky; earth deep underground. Null where nothing would gather.
     */
    @Nullable
    public static SpiritElement elementAt(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.dimensionType().ultraWarm()) {
            return SpiritElement.FIRE;
        }
        boolean water = false;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
            BlockState state = level.getBlockState(near);
            if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.TORCH)
                    || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.LANTERN) || state.is(Blocks.FURNACE) && state.getLightEmission() > 0
                    || state.getFluidState().is(FluidTags.LAVA)) {
                return SpiritElement.FIRE;
            }
            water |= state.getFluidState().is(FluidTags.WATER);
        }
        if (water) {
            return SpiritElement.WATER;
        }
        boolean sky = level.canSeeSky(pos);
        if (pos.getY() >= 100 || sky && pos.getY() > level.getSeaLevel() + 25 || sky && random.nextFloat() < 0.35F) {
            return SpiritElement.WIND;
        }
        if (!sky && pos.getY() < 50) {
            return SpiritElement.EARTH;
        }
        return null;
    }

    // ---- Bound spirits -------------------------------------------------------------------------------------------

    /** The spirits orbiting the player, oldest first. */
    public static List<WispEntity> bound(ServerPlayer player) {
        List<WispEntity> list = new java.util.ArrayList<>(player.serverLevel().getEntities(ModRegistries.WISP.get(),
                player.getBoundingBox().inflate(16), w -> w.isAlive() && w.isBoundTo(player)));
        list.sort(Comparator.comparingInt(Entity::getId));
        return list;
    }

    public static boolean hasRoom(ServerPlayer player) {
        return bound(player).size() < Config.YIFA_MAX_BOUND.get();
    }

    /**
     * Binds the wild spirit you're looking at. Sneaking gathers every wild spirit within 8 blocks instead.
     * You have to be able to see them: Spirit Sight on.
     */
    public static boolean call(ServerPlayer player) {
        if (!hasSight(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.yifa.need_sight"), true);
            return false;
        }
        if (!hasRoom(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.yifa.full", Config.YIFA_MAX_BOUND.get()), true);
            return false;
        }
        List<WispEntity> wild = player.serverLevel().getEntities(ModRegistries.WISP.get(), player.getBoundingBox().inflate(16),
                w -> w.isAlive() && w.isWild());
        int bound = 0;
        if (player.isShiftKeyDown()) {
            wild.sort(Comparator.comparingDouble(w -> w.distanceToSqr(player)));
            for (WispEntity wisp : wild) {
                if (wisp.distanceTo(player) <= 8 && hasRoom(player)) {
                    wisp.bind(player);
                    bound++;
                }
            }
        } else {
            WispEntity best = null;
            double bestDot = 0.96;
            Vec3 eye = player.getEyePosition();
            for (WispEntity wisp : wild) {
                Vec3 to = wisp.position().subtract(eye);
                double dot = to.normalize().dot(player.getLookAngle());
                if (to.length() <= 16 && dot > bestDot) {
                    best = wisp;
                    bestDot = dot;
                }
            }
            if (best != null) {
                best.bind(player);
                bound = 1;
            }
        }
        if (bound == 0) {
            player.displayClientMessage(Component.translatable("tensurafragments.yifa.no_spirit"), true);
        }
        return bound > 0;
    }

    // ---- Spirit Magic --------------------------------------------------------------------------------------------

    /**
     * Releases every bound spirit as one spell. The mix decides it: fire + wind is a travelling fire whirl, fire alone a
     * flame burst, wind alone a gale, water alone a healing spring, earth alone an earth bind. Water in a mix also
     * heals you; earth in a mix slows what it hits.
     */
    public static boolean release(ServerPlayer player) {
        List<WispEntity> spirits = bound(player);
        if (spirits.isEmpty()) {
            player.displayClientMessage(Component.translatable("tensurafragments.yifa.no_bound"), true);
            return false;
        }
        if (!Magicules.trySpend(player, Config.YIFA_MAGICULES_PER_SPIRIT.get() * spirits.size())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        Map<SpiritElement, Integer> count = new EnumMap<>(SpiritElement.class);
        for (WispEntity wisp : spirits) {
            count.merge(wisp.getElement(), 1, Integer::sum);
            wisp.spend();
        }
        int fire = count.getOrDefault(SpiritElement.FIRE, 0);
        int wind = count.getOrDefault(SpiritElement.WIND, 0);
        int water = count.getOrDefault(SpiritElement.WATER, 0);
        int earth = count.getOrDefault(SpiritElement.EARTH, 0);
        float power = Config.YIFA_MAGIC_POWER.get().floatValue();
        ServerLevel level = player.serverLevel();
        Aim aim = aim(player, 32);

        if (fire > 0 && wind > 0) {
            level.addFreshEntity(FireWhirlEntity.create(player, fire + wind, earth > 0, power));
            sound(level, player.position(), TensuraSoundEvents.CAST_FIRE.get());
            sound(level, player.position(), TensuraSoundEvents.CAST_WIND.get());
        } else if (fire > 0) {
            // A Tensura fire ball carries it; the flame burst goes off where it lands.
            FireBallProjectile ball = new FireBallProjectile(level, player);
            ball.setBurnTicks(60);
            launch(player, ball, SpiritElement.FIRE, fire, earth > 0, power, 1.6F);
            sound(level, player.position(), TensuraSoundEvents.CAST_FIRE.get());
        } else if (wind > 0) {
            // A Tensura wind blade carries it; the gale bursts where it lands.
            launch(player, new WindBladeProjectile(level, player), SpiritElement.WIND, wind, earth > 0, power, 2.0F);
            sound(level, player.position(), TensuraSoundEvents.CAST_WIND.get());
        } else if (water > 0) {
            spring(level, player, water, power);
        } else {
            earthBind(level, player, aim.point(), earth, power);
        }
        if (water > 0 && (fire > 0 || wind > 0)) {
            player.heal(2 * water * power);
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
        }
        return true;
    }

    /** What a carrier projectile holds: released spirits whose spell goes off where it ends. */
    private record Carried(UUID owner, SpiritElement element, int spirits, boolean earth, float power) {
    }

    /**
     * Carrier projectiles in flight. Kept here rather than on the projectile, because some Tensura projectiles copy
     * themselves (with all their data), which must not set the spell off twice.
     */
    private static final Map<UUID, Carried> CARRIERS = new HashMap<>();
    /** Bursts waiting for the end of the tick (they can't safely go off while an entity is being removed). */
    private static final List<Runnable> PENDING_BURSTS = new java.util.ArrayList<>();

    private static void launch(ServerPlayer player, TensuraFlyingProjectile projectile, SpiritElement element, int spirits,
                               boolean earth, float power, float speed) {
        projectile.setDamage(2);
        projectile.setSpeed(speed);
        projectile.setPosAndShoot(player);
        CARRIERS.put(projectile.getUUID(), new Carried(player.getUUID(), element, spirits, earth, power));
        player.level().addFreshEntity(projectile);
    }

    /**
     * A carrier projectile hit something (or ran out): its flame burst or gale goes off right there, at the end of
     * this tick. Only the first time; anything else leaving with the same id is ignored.
     */
    public static void onCarrierEnds(ServerLevel level, Entity projectile) {
        Carried carried = CARRIERS.remove(projectile.getUUID());
        if (carried == null || !(level.getPlayerByUUID(carried.owner()) instanceof ServerPlayer player)) {
            return;
        }
        Vec3 at = projectile.position();
        PENDING_BURSTS.add(() -> {
            if (carried.element() == SpiritElement.FIRE) {
                flameBurst(level, player, at, carried.spirits(), carried.earth(), carried.power());
            } else {
                gale(level, player, at, carried.spirits(), carried.earth(), carried.power());
            }
        });
    }

    /** Sets off the bursts whose carriers ended this tick. */
    public static void runPendingBursts() {
        if (PENDING_BURSTS.isEmpty()) {
            return;
        }
        List<Runnable> bursts = List.copyOf(PENDING_BURSTS);
        PENDING_BURSTS.clear();
        bursts.forEach(Runnable::run);
    }

    static void flameBurst(ServerLevel level, ServerPlayer player, Vec3 at, int fire, boolean earth, float power) {
        double radius = (2 + 0.6 * fire) * Math.sqrt(power);
        float damage = 5 * fire * power;
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40 + 20 * fire, radius / 2, radius / 3, radius / 2, 0.08);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 6, radius / 3, 0.2, radius / 3, 0);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.8F, (float) radius),
                at.x, at.y, at.z);
        sound(level, at, TensuraSoundEvents.CAST_FIRE.get());
        for (LivingEntity target : enemies(level, player, at, radius)) {
            hit(player, target, damage, earth);
            target.igniteForSeconds(3 + fire);
        }
    }

    static void gale(ServerLevel level, ServerPlayer player, Vec3 at, int wind, boolean earth, float power) {
        double radius = (3 + 0.6 * wind) * Math.sqrt(power);
        float damage = 2 * wind * power;
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3, at.z, 40, radius / 2, 0.4, radius / 2, 0.2);
        sound(level, at, TensuraSoundEvents.CAST_WIND.get());
        for (LivingEntity target : enemies(level, player, at, radius)) {
            hit(player, target, damage, earth);
            Vec3 away = target.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.8 + 0.1 * wind);
            target.push(away.x, 0.6 + 0.15 * wind, away.z);
            target.hurtMarked = true;
        }
        // The wind throws back projectiles that aren't yours.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(radius),
                p -> p.getOwner() != player)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1));
            projectile.hurtMarked = true;
        }
    }

    private static void spring(ServerLevel level, ServerPlayer player, int water, float power) {
        Vec3 at = player.position();
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 60, 3, 0.6, 3, 0.2);
        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 1, at.z, 30, 3, 0.6, 3, 0.05);
        sound(level, at, TensuraSoundEvents.CAST_WATER.get());
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6),
                e -> e.isAlive() && (e == player || Spell.isAlly(e, player)))) {
            ally.heal(4 * water * power);
            ally.clearFire();
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, water > 2 ? 1 : 0), player);
        }
    }

    private static void earthBind(ServerLevel level, ServerPlayer player, Vec3 at, int earth, float power) {
        double radius = (2.5 + 0.5 * earth) * Math.sqrt(power);
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                    at.x, at.y, at.z, 50, radius / 2, 0.2, radius / 2, 0.2);
        }
        sound(level, at, TensuraSoundEvents.CAST_EARTH.get());
        for (LivingEntity target : enemies(level, player, at, radius)) {
            hit(player, target, 2 * earth * power, true);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5), player);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), player);
        }
    }

    static void hit(ServerPlayer player, LivingEntity target, float damage, boolean earth) {
        DamageSource source = player.damageSources().indirectMagic(player, player);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
        if (earth) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), player);
        }
    }

    static List<LivingEntity> enemies(ServerLevel level, ServerPlayer player, Vec3 at, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e != player && !Spell.isAlly(e, player)
                        && e.getBoundingBox().getCenter().distanceTo(at) <= radius + e.getBbWidth() / 2);
    }

    // ---- Spirit Jutsu --------------------------------------------------------------------------------------------

    /** The weak fallback: a small fire or wind jutsu from your own magicules, no spirits needed. */
    public static boolean jutsu(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now < JUTSU_READY.getOrDefault(player.getUUID(), 0L)) {
            return false;
        }
        if (!Magicules.trySpend(player, Config.YIFA_JUTSU_COST.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        JUTSU_READY.put(player.getUUID(), now + 10);
        boolean wind = player.getData(ModRegistries.YIFA_JUTSU) == 1;
        TensuraFlyingProjectile projectile = wind ? new WindBladeProjectile(player.level(), player)
                : new FireBoltProjectile(player.level(), player);
        projectile.setDamage(wind ? 2.5F : 3.0F);
        projectile.setSpeed(1.6F);
        if (!wind) {
            projectile.setBurnTicks(40);
        }
        projectile.setPosAndShoot(player);
        player.level().addFreshEntity(projectile);
        sound(player.serverLevel(), player.position(), wind ? TensuraSoundEvents.CAST_WIND.get() : TensuraSoundEvents.CAST_FIRE.get());
        return true;
    }

    public static void cycleJutsu(ServerPlayer player) {
        int next = 1 - player.getData(ModRegistries.YIFA_JUTSU);
        player.setData(ModRegistries.YIFA_JUTSU, next);
        player.displayClientMessage(Component.translatable("tensurafragments.yifa.jutsu_selected",
                Component.translatable("tensurafragments.yifa.element." + (next == 1 ? "wind" : "fire"))), true);
        sync(player);
    }

    // ---- Magisteel items -----------------------------------------------------------------------------------------

    /**
     * Magisteel Spirit Bell: every wild spirit within range comes flying and binds itself to you (while there's room).
     * If there are hardly any, the ringing calls a couple out of their element nearby first.
     */
    public static int ringBell(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double range = Config.SPIRIT_BELL_RANGE.get();
        List<WispEntity> wild = level.getEntities(ModRegistries.WISP.get(), player.getBoundingBox().inflate(range),
                w -> w.isAlive() && w.isWild());
        for (int i = wild.size(); i < 2; i++) {
            WispEntity called = trySpawnNear(level, player, 10);
            if (called != null) {
                wild.add(called);
            }
        }
        for (WispEntity wisp : wild) {
            wisp.lure(player, true);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.6F, 4.0F),
                player.getX(), player.getY() + 1, player.getZ());
        return wild.size();
    }

    /** Carrying a Magisteel Spirit Lantern anywhere in your inventory draws spirits in (and makes more gather). */
    public static boolean carriesLantern(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModRegistries.SPIRIT_LANTERN.get())) {
                return true;
            }
        }
        return false;
    }

    // ---- Shared --------------------------------------------------------------------------------------------------

    record Aim(Vec3 point, @Nullable LivingEntity entity) {
    }

    static Aim aim(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, area,
                e -> e instanceof LivingEntity living && living.isAlive() && !Spell.isAlly(living, player));
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            return new Aim(target.getBoundingBox().getCenter(), target);
        }
        return new Aim(end, null);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SPIRIT_COMMUNION.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_SPIRIT_COMMUNION.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.SPIRIT_COMMUNION.get());
        }
    }
}
