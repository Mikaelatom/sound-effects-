package com.tensurafragments.card;

import com.tensurafragments.ModRegistries;
import java.util.List;
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
 * A Spell Card tornado: Gale's whirlwind, the Fire Tornado combo (travelling, burning) and the Thunderstorm combo
 * (lightning on everything around it). It drags enemies in from a wide area, lifts what it catches, and grinds them.
 * Drawn with Tensura's magic tornado model.
 */
public class CardTornadoEntity extends Entity implements GeoEntity {
    public enum Variant {
        WIND, FIRE, STORM
    }

    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(CardTornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(CardTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X =
            SynchedEntityData.defineId(CardTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z =
            SynchedEntityData.defineId(CardTornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation ANIMATION = RawAnimation.begin()
            .thenPlay("animation.magic_tornado.start").thenLoop("animation.magic_tornado.loop");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerUuid;
    private int duration = 100;
    private float damage = 4;
    private double pullRadius = 8;

    public CardTornadoEntity(EntityType<? extends CardTornadoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * @param drift   movement per tick (zero: it stays put)
     * @param damage  dealt to everything caught in it, twice a second
     */
    public static CardTornadoEntity spawn(ServerPlayer owner, Variant variant, Vec3 at, Vec3 drift, double radius,
                                          int duration, float damage, double pullRadius) {
        CardTornadoEntity tornado = new CardTornadoEntity(ModRegistries.CARD_TORNADO.get(), owner.level());
        tornado.ownerUuid = owner.getUUID();
        tornado.entityData.set(VARIANT, variant.ordinal());
        tornado.entityData.set(RADIUS, (float) radius);
        tornado.entityData.set(DIR_X, (float) drift.x);
        tornado.entityData.set(DIR_Z, (float) drift.z);
        tornado.duration = duration;
        tornado.damage = damage;
        tornado.pullRadius = pullRadius;
        // Stand it on the ground under the point.
        HitResult ground = owner.level().clip(new ClipContext(at, at.add(0, -6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, tornado));
        Vec3 base = ground.getType() == HitResult.Type.MISS ? at.subtract(0, 1, 0) : ground.getLocation();
        tornado.setPos(base.x, base.y, base.z);
        owner.level().addFreshEntity(tornado);
        owner.level().playSound(null, base.x, base.y, base.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.5F, 0.6F);
        return tornado;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, 0);
        builder.define(RADIUS, 2.5F);
        builder.define(DIR_X, 0F);
        builder.define(DIR_Z, 0F);
    }

    public Variant variant() {
        return Variant.values()[Math.floorMod(entityData.get(VARIANT), Variant.values().length)];
    }

    public double radius() {
        return entityData.get(RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        travel();
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        ServerPlayer owner = ownerUuid == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerUuid);
        if (owner == null || tickCount > duration) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 30, radius() / 2, 1, radius() / 2, 0.05);
            discard();
            return;
        }
        double radius = radius();
        Variant variant = variant();
        particles(level, radius, variant);
        if (tickCount % 20 == 0) {
            level.playSound(null, getX(), getY(), getZ(), variant == Variant.FIRE ? SoundEvents.FIRECHARGE_USE
                    : SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1F, 0.5F);
        }
        Vec3 centre = position().add(0, radius, 0);
        for (LivingEntity target : SpellCards.enemies(level, owner, centre, pullRadius)) {
            Vec3 in = position().subtract(target.position()).multiply(1, 0, 1);
            double distance = in.length();
            boolean caught = distance <= radius + target.getBbWidth() / 2;
            if (distance > 1.0E-3) {
                in = in.scale(1 / distance);
            }
            // Dragged in from far off; whirled round and lifted once it's caught.
            Vec3 swirl = new Vec3(-in.z, 0, in.x).scale(caught ? 0.25 : 0.08);
            Vec3 pull = in.scale(caught ? 0.06 : 0.18);
            target.setDeltaMovement(target.getDeltaMovement().scale(caught ? 0.6 : 1).add(pull).add(swirl)
                    .add(0, caught ? 0.12 : 0, 0));
            target.hurtMarked = true;
            target.resetFallDistance();
            if (caught && tickCount % 10 == 0) {
                SpellCards.hurt(owner, target, damage);
                if (variant == Variant.FIRE) {
                    target.igniteForSeconds(6);
                }
            }
        }
        if (variant == Variant.STORM && tickCount % 15 == 0) {
            List<LivingEntity> around = SpellCards.enemies(level, owner, centre, pullRadius + 2);
            if (!around.isEmpty()) {
                LivingEntity struck = around.get(random.nextInt(around.size()));
                SpellCards.bolt(level, struck.position());
                SpellCards.hurt(owner, struck, damage * 3);
            }
        }
    }

    private void particles(ServerLevel level, double radius, Variant variant) {
        for (int i = 0; i < 6; i++) {
            double angle = tickCount * 0.6 + i * 1.05;
            double height = random.nextDouble() * radius * 2.2;
            double r = radius * (0.3 + height / (radius * 2.2) * 0.8);
            double x = getX() + Math.cos(angle) * r;
            double z = getZ() + Math.sin(angle) * r;
            switch (variant) {
                case FIRE -> level.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, x, getY() + height, z,
                        1, 0, 0.05, 0, 0.01);
                case STORM -> level.sendParticles(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CLOUD, x,
                        getY() + height, z, 1, 0, 0.05, 0, 0.02);
                default -> level.sendParticles(ParticleTypes.CLOUD, x, getY() + height, z, 1, 0, 0.05, 0, 0.02);
            }
        }
    }

    /** Moves along the ground the way it drifts, stopping at walls. */
    private void travel() {
        Vec3 drift = new Vec3(entityData.get(DIR_X), 0, entityData.get(DIR_Z));
        if (drift.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 next = position().add(drift);
        Vec3 up = next.add(0, 1.2, 0);
        HitResult wall = level().clip(new ClipContext(position().add(0, 1.2, 0), up, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            return;
        }
        HitResult ground = level().clip(new ClipContext(up, up.add(0, -4, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        double y = ground.getType() == HitResult.Type.MISS ? next.y - 0.3 : ground.getLocation().y;
        setPos(next.x, y, next.z);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tornado", 0, state -> state.setAndContinue(ANIMATION)));
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
        return distance < 128 * 128;
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
}
