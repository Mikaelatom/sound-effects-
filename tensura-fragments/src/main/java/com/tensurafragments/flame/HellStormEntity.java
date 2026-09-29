package com.tensurafragments.flame;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.shikigami.Spell;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
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
        HellStormParts.follow(storm, caster);
        return storm;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
        builder.define(LENGTH, 18.0F);
    }

    public float getLength() {
        return entityData.get(LENGTH);
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

        // Flames along the storm and a burst where it hits.
        for (int i = 0; i < 6; i++) {
            Vec3 p = from.add(direction.scale(random.nextDouble() * length));
            level.sendParticles(random.nextBoolean() ? ParticleTypes.FLAME : TensuraParticleTypes.RED_FIRE.get(),
                    p.x, p.y, p.z, 1, 0.6, 0.6, 0.6, 0.05);
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
        double width = Config.HELL_STORM_WIDTH.get();
        AABB box = new AABB(from, to).inflate(width);
        DamageSource source = level.damageSources().source(TensuraDamageTypes.BLACK_FLAME, this, caster);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !Spell.isAlly(e, caster))) {
            Vec3 centre = target.position().add(0, target.getBbHeight() / 2, 0);
            if (distanceToSegment(centre, from, to) > width + target.getBbWidth() / 2) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(source, Config.HELL_STORM_DAMAGE.get().floatValue());
            target.addEffect(new MobEffectInstance(ModRegistries.DRACONIC_HELLFIRE, MobEffectInstance.INFINITE_DURATION, 0,
                    false, true, true), caster);
        }
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-9 ? 0 : Math.max(0, Math.min(1, point.subtract(a).dot(ab) / lengthSqr));
        return point.distanceTo(a.add(ab.scale(t)));
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
