package com.tensurafragments.flame;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.shikigami.Spell;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Draconic Hell Storm, part two: Gluttony's devouring mist, turned to hellfire, pouring from the magic circle where
 * the caster looks. Everything in it takes huge fire damage four times a second and is left with Draconic Hellfire,
 * a burn that never goes out.
 */
public class HellStormEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> CASTER =
            SynchedEntityData.defineId(HellStormEntity.class, EntityDataSerializers.INT);
    /** How far the storm reaches right now (it stops at walls). Synced so the model is drawn the right length. */
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(HellStormEntity.class, EntityDataSerializers.FLOAT);
    /** The cone's radius at its far end. Synced so the model is drawn exactly as wide as it burns. */
    private static final EntityDataAccessor<Float> END_RADIUS =
            SynchedEntityData.defineId(HellStormEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation ANIMATION = RawAnimation.begin()
            .thenPlay("animation.gluttony_mist.start").thenLoop("animation.gluttony_mist.loop");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int duration = 60;

    public HellStormEntity(EntityType<? extends HellStormEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static HellStormEntity create(ServerPlayer caster, int duration) {
        HellStormEntity storm = new HellStormEntity(ModRegistries.HELL_STORM.get(), caster.level());
        storm.entityData.set(CASTER, caster.getId());
        storm.duration = duration;
        storm.entityData.set(END_RADIUS, Config.HELL_STORM_END_RADIUS.get().floatValue());
        HellStormParts.follow(storm, caster);
        return storm;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
        builder.define(LENGTH, 18.0F);
        builder.define(END_RADIUS, 5.0F);
    }

    public int getCasterId() {
        return entityData.get(CASTER);
    }

    public float getLength() {
        return entityData.get(LENGTH);
    }

    public float getEndRadius() {
        return entityData.get(END_RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerPlayer caster = HellStormParts.caster(this, entityData.get(CASTER));
        if (caster == null || tickCount > duration) {
            discard();
            return;
        }
        HellStormParts.follow(this, caster);
        ServerLevel level = (ServerLevel) level();
        Vec3 from = position();
        Vec3 direction = caster.getLookAngle();
        double range = Config.HELL_STORM_RANGE.get();
        HitResult wall = level.clip(new ClipContext(from, from.add(direction.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        double length = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from);
        entityData.set(LENGTH, (float) length);
        Vec3 to = from.add(direction.scale(length));

        // Flames through the cone and a burst where it hits.
        for (int i = 0; i < 8; i++) {
            double along = random.nextDouble() * length;
            double spread = HellStormParts.radiusAt(along, length, getEndRadius()) * 0.6;
            Vec3 p = from.add(direction.scale(along));
            level.sendParticles(random.nextBoolean() ? ParticleTypes.FLAME : TensuraParticleTypes.RED_FIRE.get(),
                    p.x, p.y, p.z, 1, spread, spread, spread, 0.05);
        }
        level.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 3, 0.8, 0.4, 0.8, 0);
        if (tickCount % 10 == 0) {
            level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }

        if (tickCount % 5 == 0) {
            burn(level, caster, from, to);
        }
    }

    private void burn(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to) {
        double endRadius = getEndRadius();
        double length = from.distanceTo(to);
        Vec3 direction = to.subtract(from).normalize();
        AABB box = new AABB(from, to).inflate(endRadius);
        DamageSource source = HellfireDamage.storm(this, caster);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !Spell.isAlly(e, caster))) {
            if (!insideCone(target, from, direction, length, endRadius)) {
                continue;
            }
            target.invulnerableTime = 0;
            com.tensurafragments.skill.EpScaling.hurt(caster, target, source, Config.HELL_STORM_DAMAGE.get().floatValue());
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
            // The lasting burn scales too, through its level.
            int burnLevel = Math.min(9, Math.max(0, Math.round(com.tensurafragments.skill.EpScaling.multiplier(caster)) - 1));
            target.addEffect(new MobEffectInstance(ModRegistries.DRACONIC_HELLFIRE, MobEffectInstance.INFINITE_DURATION, burnLevel,
                    false, true, true), caster);
        }
    }

    /** Whether any of the target's body is inside the mist's cone (the same shape the client draws). */
    private static boolean insideCone(LivingEntity target, Vec3 from, Vec3 direction, double length, double endRadius) {
        double reach = target.getBbWidth() / 2;
        double halfHeight = target.getBbHeight() / 2;
        Vec3 centre = target.position().add(0, halfHeight, 0);
        double along = centre.subtract(from).dot(direction);
        // Anything behind the hand is out, however wide the storm.
        if (along < -reach || along > length + reach) {
            return false;
        }
        double clamped = Math.max(0, Math.min(length, along));
        Vec3 axisPoint = from.add(direction.scale(clamped));
        // Nearest point of the target's box to the storm's centre line.
        AABB body = target.getBoundingBox();
        Vec3 nearest = new Vec3(Math.max(body.minX, Math.min(body.maxX, axisPoint.x)),
                Math.max(body.minY, Math.min(body.maxY, axisPoint.y)),
                Math.max(body.minZ, Math.min(body.maxZ, axisPoint.z)));
        return nearest.distanceTo(axisPoint) <= HellStormParts.radiusAt(clamped, length, endRadius);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "storm", 0, state -> state.setAndContinue(ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
