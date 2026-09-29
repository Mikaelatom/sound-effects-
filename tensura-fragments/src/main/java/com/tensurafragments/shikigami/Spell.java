package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.magic.Blast;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The spells a Shikigami Control talisman can carry, after Seika's five-phase onmyōdō. Each one goes off when its
 * talisman touches the ground or a creature. Only the Explosive talisman can hurt its caster; the elemental ones
 * spare the caster and their allies. {@code potency} is 1 for paper and lower for leaves.
 */
public enum Spell {
    EXPLOSIVE("explosive", 0xFFD04040, 20) {
        @Override
        public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            double radius = Config.TALISMAN_BLAST_RADIUS.get() * (0.5 + 0.5 * potency);
            Blast.explode(level, talisman, owner, at, radius, Config.TALISMAN_BLAST_DAMAGE.get().floatValue(),
                    Config.BLAST_KNOCKBACK.get(), Config.SELF_DAMAGE_MULTIPLIER.get().floatValue(), potency);
        }
    },
    FIRE("fire", 0xFFFF8A30, 25) {
        @Override
        public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, 3.0)) {
                target.hurt(magic(level, talisman, owner), 4.0F * potency);
                target.igniteForSeconds(5 * potency);
            }
            level.sendParticles(TensuraParticleTypes.RED_FIRE.get(), at.x, at.y + 0.3, at.z, 40, 1.2, 0.4, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 1.0, 0.3, 1.0, 0.08);
            sound(level, at, TensuraSoundEvents.CAST_FIRE.get());
        }
    },
    WATER("water", 0xFF4AA8FF, 20) {
        @Override
        public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, 4.0)) {
                target.hurt(magic(level, talisman, owner), 2.0F * potency);
                Vec3 push = target.position().subtract(at).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : push.normalize().scale(1.4 * potency);
                target.push(push.x, 0.35, push.z);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(60 * potency), 1));
            }
            // The surge puts out fire, on anyone nearby (including you) and on the ground.
            for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(4.0), Entity::isOnFire)) {
                entity.clearFire();
            }
            BlockPos centre = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-3, -2, -3), centre.offset(3, 2, 3))) {
                if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                    level.removeBlock(pos, false);
                }
            }
            level.sendParticles(TensuraParticleUtils.getWaterBubble(), at.x, at.y + 0.3, at.z, 30, 1.5, 0.4, 1.5, 0.1);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3, at.z, 60, 1.5, 0.3, 1.5, 0.3);
            sound(level, at, TensuraSoundEvents.CAST_WATER.get());
        }
    },
    WOOD("wood", 0xFF5FC45A, 25) {
        @Override
        public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            int duration = Math.round(60 * potency);
            for (LivingEntity target : enemies(level, owner, at, 3.0)) {
                // Roots: held in place and weakened.
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
                target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                target.hurtMarked = true;
            }
            for (LivingEntity ally : allies(level, owner, at, 3.0)) {
                ally.heal(4.0F * potency);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0));
            }
            level.sendParticles(TensuraParticleTypes.BLOSSOM.get(), at.x, at.y + 0.5, at.z, 30, 1.2, 0.6, 1.2, 0.02);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.3, at.z, 25, 1.2, 0.4, 1.2, 0.0);
            sound(level, at, TensuraSoundEvents.GENERIC_HEAL.get());
        }
    },
    LIGHTNING("lightning", 0xFFFFE04A, 35) {
        @Override
        public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            List<LivingEntity> hit = new ArrayList<>();
            Vec3 from = at;
            float damage = 8.0F * potency;
            // Strike the nearest enemy, then jump to the next nearest (up to 3 targets), weaker each time.
            for (int jump = 0; jump < 3; jump++) {
                Vec3 origin = from;
                LivingEntity next = enemies(level, owner, from, jump == 0 ? 4.0 : 5.0).stream()
                        .filter(e -> !hit.contains(e))
                        .min(Comparator.comparingDouble(e -> e.distanceToSqr(origin)))
                        .orElse(null);
                if (next == null) {
                    break;
                }
                hit.add(next);
                next.hurt(magic(level, talisman, owner), damage);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(next.getX(), next.getY(), next.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                level.sendParticles(TensuraParticleTypes.YELLOW_LIGHTNING_SPARK.get(), next.getX(), next.getY() + 1, next.getZ(),
                        15, 0.3, 0.6, 0.3, 0.1);
                from = next.position();
                damage *= 0.65F;
            }
            level.sendParticles(TensuraParticleTypes.LIGHTNING_SPARK.get(), at.x, at.y + 0.3, at.z, 20, 0.6, 0.3, 0.6, 0.1);
            sound(level, at, TensuraSoundEvents.CAST_LIGHTNING.get());
        }
    },
    EARTH("earth", 0xFFB08050, 30) {
        @Override
        public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, 3.0)) {
                target.hurt(magic(level, talisman, owner), 5.0F * potency);
                target.setDeltaMovement(target.getDeltaMovement().x * 0.2, 0.9 * potency, target.getDeltaMovement().z * 0.2);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(40 * potency), 1));
            }
            BlockState ground = level.getBlockState(BlockPos.containing(at).below());
            if (ground.isAir()) {
                ground = level.getBlockState(BlockPos.containing(at));
            }
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z,
                        60, 1.5, 0.3, 1.5, 0.3);
            }
            TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessWave(0.8F, 3.0F), at.x, at.y + 0.1, at.z);
            sound(level, at, TensuraSoundEvents.CAST_EARTH.get());
        }
    };

    private final String id;
    private final int colour;
    private final double magiculeCost;

    Spell(String id, int colour, double magiculeCost) {
        this.id = id;
        this.colour = colour;
        this.magiculeCost = magiculeCost;
    }

    /** Lower-case name, used for textures and translation keys. */
    public String id() {
        return id;
    }

    /** ARGB colour of this talisman's ink and seal. */
    public int colour() {
        return colour;
    }

    public double magiculeCost() {
        return magiculeCost * Config.SPELL_COST_MULTIPLIER.get();
    }

    public Spell next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Spell byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }

    /** Sets the spell off at {@code at}. */
    public abstract void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency);

    private static DamageSource magic(ServerLevel level, Entity talisman, @Nullable Entity owner) {
        return level.damageSources().indirectMagic(talisman, owner);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    static boolean isAlly(Entity entity, @Nullable Entity owner) {
        return owner != null && (entity == owner
                || (entity instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID()))
                || Binding.isBoundTo(entity, owner));
    }

    private static List<LivingEntity> inRange(ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at) <= radius + 0.5);
    }

    static List<LivingEntity> enemies(ServerLevel level, @Nullable Entity owner, Vec3 at, double radius) {
        return inRange(level, at, radius).stream().filter(e -> !isAlly(e, owner)).toList();
    }

    static List<LivingEntity> allies(ServerLevel level, @Nullable Entity owner, Vec3 at, double radius) {
        return inRange(level, at, radius).stream().filter(e -> isAlly(e, owner)).toList();
    }
}
