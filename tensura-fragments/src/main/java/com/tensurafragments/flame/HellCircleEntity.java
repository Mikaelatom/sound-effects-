package com.tensurafragments.flame;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Draconic Hell Storm, part one: Tensura's flame magic circle on the caster's hand. It charges, then releases the
 * {@link HellStormEntity} and stays until the storm ends.
 */
public class HellCircleEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> CASTER =
            SynchedEntityData.defineId(HellCircleEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation ANIMATION = RawAnimation.begin()
            .thenPlay("animation.magic_circle.start").thenLoop("animation.magic_circle.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int charge = 30;
    private int duration = 60;

    public HellCircleEntity(EntityType<? extends HellCircleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static HellCircleEntity create(ServerPlayer caster) {
        HellCircleEntity circle = new HellCircleEntity(ModRegistries.HELL_CIRCLE.get(), caster.level());
        circle.entityData.set(CASTER, caster.getId());
        circle.charge = Config.HELL_STORM_CHARGE_TICKS.get();
        circle.duration = Config.HELL_STORM_DURATION_TICKS.get();
        HellStormParts.follow(circle, caster);
        return circle;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CASTER, -1);
    }

    public int getCasterId() {
        return entityData.get(CASTER);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerPlayer caster = HellStormParts.caster(this, entityData.get(CASTER));
        if (caster == null || tickCount > charge + duration) {
            discard();
            return;
        }
        HellStormParts.follow(this, caster);
        ServerLevel level = (ServerLevel) level();
        Vec3 at = position();
        if (tickCount < charge) {
            // Fire spirals in as it charges.
            double angle = tickCount * 0.6;
            for (int i = 0; i < 3; i++) {
                double a = angle + i * Math.PI * 2 / 3;
                double r = 1.2 - tickCount / (double) Math.max(1, charge);
                level.sendParticles(TensuraParticleTypes.RED_FIRE.get(), at.x + Math.cos(a) * r, at.y + Math.sin(a) * r * 0.5,
                        at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.01);
            if (tickCount == 1) {
                level.playSound(null, at.x, at.y, at.z, TensuraSoundEvents.CAST_FIRE.get(), SoundSource.PLAYERS, 1.2F, 0.6F);
            }
        } else if (tickCount == charge) {
            level.addFreshEntity(HellStormEntity.create(caster, duration));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 0.8F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.5F);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "circle", 0, state -> state.setAndContinue(ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard(); // a cast doesn't survive a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
