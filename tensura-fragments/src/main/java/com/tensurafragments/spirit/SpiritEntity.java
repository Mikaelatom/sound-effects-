package com.tensurafragments.spirit;

import com.tensurafragments.ModRegistries;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A spirit called by Spirit Control. It appears, turns to its target, does its one attack on cue with its animation
 * ({@link SpiritAttacks}), and fades away. Never saved: it only lives for a second or so.
 */
public class SpiritEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private ServerPlayer owner;
    @Nullable
    private LivingEntity target;
    private Vec3 aim = Vec3.ZERO;
    /** Where a pouncing spirit (the Blade Tiger) started from. */
    private Vec3 start = Vec3.ZERO;
    private boolean struck;

    public SpiritEntity(EntityType<? extends SpiritEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** A spirit at {@code at}, about to attack {@code target} (if any) or the point {@code aim}. */
    public static SpiritEntity create(ServerPlayer owner, SpiritKind kind, Vec3 at, @Nullable LivingEntity target, Vec3 aim) {
        SpiritEntity spirit = new SpiritEntity(ModRegistries.SPIRIT.get(), owner.level());
        spirit.entityData.set(KIND, kind.ordinal());
        spirit.owner = owner;
        spirit.target = target;
        spirit.aim = aim;
        spirit.start = at;
        spirit.setPos(at.x, at.y, at.z);
        spirit.face(aim);
        return spirit;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
    }

    public SpiritKind getKind() {
        return SpiritKind.byIndex(entityData.get(KIND));
    }

    @Nullable
    ServerPlayer owner() {
        return owner;
    }

    @Nullable
    LivingEntity target() {
        return target;
    }

    /** Where it's attacking: the target's middle while it lives, otherwise the point it was aimed at. */
    Vec3 aim() {
        if (target != null && target.isAlive()) {
            aim = target.getBoundingBox().getCenter();
        }
        return aim;
    }

    Vec3 start() {
        return start;
    }

    public boolean hasStruck() {
        return struck;
    }

    private void face(Vec3 point) {
        Vec3 to = point.subtract(position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        yRotO = yaw;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        SpiritKind kind = getKind();
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            vanish(level);
            return;
        }
        if (tickCount == 1) {
            TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.8F, 2.0F),
                    getX(), getY() + 0.8, getZ());
            level.playSound(null, getX(), getY(), getZ(), TensuraSoundEvents.CAST_LIGHT.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        if (!struck) {
            face(aim());
        }
        if (kind == SpiritKind.BLADE_TIGER) {
            pounce(kind);
        }
        if (!struck && tickCount >= kind.strikeTick()) {
            struck = true;
            SpiritAttacks.strike(this, level, owner);
        }
        if (tickCount >= kind.lifetime()) {
            vanish(level);
        }
    }

    /** The Blade Tiger closes the gap in the few ticks before its strike, and carries on a little past. */
    private void pounce(SpiritKind kind) {
        int from = kind.strikeTick() - 4;
        int to = kind.strikeTick() + 1;
        if (tickCount < from || tickCount > to) {
            return;
        }
        Vec3 target = aim();
        Vec3 dir = target.subtract(start).multiply(1, 0, 1);
        Vec3 end = dir.lengthSqr() < 1.0E-4 ? target : target.add(dir.normalize().scale(1.5));
        double t = (tickCount - from) / (double) (to - from);
        Vec3 at = start.lerp(new Vec3(end.x, start.y, end.z), t);
        setPos(at.x, at.y, at.z);
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.8, getZ(), 12, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        discard();
    }

    /** 0 to 1 to 0: how solid it looks, for fading in and out. */
    public float visibility(float partialTick) {
        float age = tickCount + partialTick;
        int life = getKind().lifetime();
        return Mth.clamp(Math.min(age / 4F, (life - age) / 5F), 0F, 1F);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "attack", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenPlay(getKind().attackAnimation()))));
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
}
