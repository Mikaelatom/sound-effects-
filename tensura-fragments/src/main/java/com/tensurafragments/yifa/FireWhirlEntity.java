package com.tensurafragments.yifa;

import com.tensurafragments.ModRegistries;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Fire and wind spirits released together: the wind fans the flames into a whirl that travels the way you looked,
 * dragging enemies in, lifting them and setting them alight. Drawn with Tensura's own magic tornado, burned orange.
 */
public class FireWhirlEntity extends Entity implements GeoEntity {
    /** Spirits that went into it; drives its size, damage and how long it lasts. */
    private static final EntityDataAccessor<Float> STRENGTH =
            SynchedEntityData.defineId(FireWhirlEntity.class, EntityDataSerializers.FLOAT);
    /** Which way it travels, synced so your own game moves it smoothly too. */
    private static final EntityDataAccessor<Float> DIR_X =
            SynchedEntityData.defineId(FireWhirlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z =
            SynchedEntityData.defineId(FireWhirlEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation ANIMATION = RawAnimation.begin()
            .thenPlay("animation.magic_tornado.start").thenLoop("animation.magic_tornado.loop");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerUuid;
    private boolean earth;
    private float power = 1;
    private int duration = 80;

    public FireWhirlEntity(EntityType<? extends FireWhirlEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static FireWhirlEntity create(ServerPlayer owner, int spirits, boolean earth, float power) {
        FireWhirlEntity whirl = new FireWhirlEntity(ModRegistries.FIRE_WHIRL.get(), owner.level());
        whirl.entityData.set(STRENGTH, spirits * (float) Math.sqrt(power));
        whirl.ownerUuid = owner.getUUID();
        Vec3 direction = Vec3.directionFromRotation(0, owner.getYRot());
        whirl.entityData.set(DIR_X, (float) direction.x);
        whirl.entityData.set(DIR_Z, (float) direction.z);
        whirl.earth = earth;
        whirl.power = power;
        whirl.duration = 60 + 10 * spirits;
        Vec3 start = owner.position().add(direction.scale(2.5));
        whirl.setPos(start.x, owner.getY(), start.z);
        return whirl;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STRENGTH, 2.0F);
        builder.define(DIR_X, 0F);
        builder.define(DIR_Z, 0F);
    }

    public float getStrength() {
        return entityData.get(STRENGTH);
    }

    /** Radius of the whirl (and what it burns). */
    public double radius() {
        return 1.5 + 0.25 * getStrength();
    }

    @Override
    public void tick() {
        super.tick();
        // Both sides move it, so it glides smoothly on screen; the server's position still wins.
        travel(level());
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        ServerPlayer owner = ownerUuid == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerUuid);
        if (owner == null || tickCount > duration) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.6, 1, 0.6, 0.02);
            discard();
            return;
        }
        double radius = radius();
        for (int i = 0; i < 6; i++) {
            double angle = (tickCount * 0.6 + i) * 1.1;
            double height = random.nextDouble() * radius * 2;
            double r = radius * (0.3 + height / (radius * 2) * 0.7);
            level.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                    getX() + Math.cos(angle) * r, getY() + height, getZ() + Math.sin(angle) * r, 1, 0, 0.05, 0, 0.01);
        }
        if (tickCount % 20 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 0.7F);
        }
        Vec3 centre = position().add(0, radius, 0);
        for (LivingEntity target : SpiritCommunion.enemies(level, owner, centre, radius + 0.5)) {
            // Drawn in and lifted.
            Vec3 in = position().subtract(target.position()).multiply(1, 0, 1);
            if (in.lengthSqr() > 1.0E-4) {
                in = in.normalize().scale(0.15);
            }
            target.setDeltaMovement(target.getDeltaMovement().add(in.x, 0.08, in.z));
            target.hurtMarked = true;
            if (tickCount % 10 == 0) {
                SpiritCommunion.hit(owner, target, 3 * getStrength() * (float) Math.sqrt(power), earth);
                target.igniteForSeconds(4);
            }
        }
    }

    /** Moves along the ground the way its caster looked, stopping at walls. */
    private void travel(Level level) {
        Vec3 heading = new Vec3(entityData.get(DIR_X), 0, entityData.get(DIR_Z));
        Vec3 next = position().add(heading.scale(0.3));
        Vec3 up = next.add(0, 1.2, 0);
        HitResult wall = level.clip(new ClipContext(position().add(0, 1.2, 0), up, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            return;
        }
        HitResult ground = level.clip(new ClipContext(up, up.add(0, -4, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        double y = ground.getType() == HitResult.Type.MISS ? next.y - 0.3 : ground.getLocation().y;
        setPos(next.x, y, next.z);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "whirl", 0, state -> state.setAndContinue(ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    /** For tests. */
    public Vec3 direction() {
        return new Vec3(entityData.get(DIR_X), 0, entityData.get(DIR_Z));
    }
}
