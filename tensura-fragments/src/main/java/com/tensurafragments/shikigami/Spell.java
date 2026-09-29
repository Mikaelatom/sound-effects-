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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The spells a Shikigami Control talisman can carry, after Seika's onmyōdō. Each one goes off when its talisman
 * touches the ground or a creature. Only the Explosive talisman can hurt its caster; the elemental ones spare the
 * caster and their allies. {@code potency} is 1 for paper and lower for leaves.
 * <p>
 * Each spell is split into its {@link #effect mechanics} and its {@link #visuals}, so a <b>rainbow</b> talisman can
 * keep a spell's shape while swapping in rainbow visuals and adding every element's damage at once (see
 * {@link #castRainbow}).
 */
public enum Spell {
    EXPLOSIVE("explosive", 0xFFD04040, 20, 3.0, 0) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            double radius = Config.TALISMAN_BLAST_RADIUS.get() * (0.5 + 0.5 * potency);
            Blast.explode(level, talisman, owner, at, radius, Config.TALISMAN_BLAST_DAMAGE.get().floatValue(),
                    Config.BLAST_KNOCKBACK.get(), Config.SELF_DAMAGE_MULTIPLIER.get().floatValue(), potency);
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            // Blast.explode draws its own explosion.
        }
    },
    FIRE("fire", 0xFFFF8A30, 25, 3.0, 4) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, radius())) {
                target.hurt(magic(level, talisman, owner), 4.0F * potency);
                target.igniteForSeconds(5 * potency);
            }
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            level.sendParticles(TensuraParticleTypes.RED_FIRE.get(), at.x, at.y + 0.3, at.z, 40, 1.2, 0.4, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 1.0, 0.3, 1.0, 0.08);
            sound(level, at, TensuraSoundEvents.CAST_FIRE.get());
        }
    },
    WATER("water", 0xFF4AA8FF, 20, 4.0, 2) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, radius())) {
                target.hurt(magic(level, talisman, owner), 2.0F * potency);
                Vec3 push = target.position().subtract(at).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(1.4 * potency);
                target.push(push.x, 0.35, push.z);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(60 * potency), 1));
            }
            extinguish(level, at, radius());
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            level.sendParticles(TensuraParticleUtils.getWaterBubble(), at.x, at.y + 0.3, at.z, 30, 1.5, 0.4, 1.5, 0.1);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3, at.z, 60, 1.5, 0.3, 1.5, 0.3);
            sound(level, at, TensuraSoundEvents.CAST_WATER.get());
        }
    },
    WOOD("wood", 0xFF5FC45A, 25, 3.0, 0) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            int duration = Math.round(60 * potency);
            for (LivingEntity target : enemies(level, owner, at, radius())) {
                // Roots: held in place and weakened.
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
                target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                target.hurtMarked = true;
            }
            for (LivingEntity ally : allies(level, owner, at, radius())) {
                ally.heal(4.0F * potency);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0));
            }
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            level.sendParticles(TensuraParticleTypes.BLOSSOM.get(), at.x, at.y + 0.5, at.z, 30, 1.2, 0.6, 1.2, 0.02);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.3, at.z, 25, 1.2, 0.4, 1.2, 0.0);
            sound(level, at, TensuraSoundEvents.GENERIC_HEAL.get());
        }
    },
    LIGHTNING("lightning", 0xFFFFE04A, 35, 4.0, 8) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            List<LivingEntity> hit = new ArrayList<>();
            Vec3 from = at;
            float damage = 8.0F * potency;
            // Strike the nearest enemy, then jump to the next nearest (up to 3 targets), weaker each time.
            for (int jump = 0; jump < 3; jump++) {
                Vec3 origin = from;
                LivingEntity next = enemies(level, owner, from, jump == 0 ? radius() : 5.0).stream()
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
                from = next.position();
                damage *= 0.65F;
            }
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            level.sendParticles(TensuraParticleTypes.YELLOW_LIGHTNING_SPARK.get(), at.x, at.y + 0.5, at.z, 25, 0.8, 0.5, 0.8, 0.1);
            level.sendParticles(TensuraParticleTypes.LIGHTNING_SPARK.get(), at.x, at.y + 0.3, at.z, 20, 0.6, 0.3, 0.6, 0.1);
            sound(level, at, TensuraSoundEvents.CAST_LIGHTNING.get());
        }
    },
    EARTH("earth", 0xFFB08050, 30, 3.0, 5) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, radius())) {
                target.hurt(magic(level, talisman, owner), 5.0F * potency);
                target.setDeltaMovement(target.getDeltaMovement().x * 0.2, 0.9 * potency, target.getDeltaMovement().z * 0.2);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(40 * potency), 1));
            }
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
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
    },
    ICE("ice", 0xFF9FE8FF, 25, 3.0, 3) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, radius())) {
                target.hurt(magic(level, talisman, owner), 3.0F * potency);
                freeze(target, potency);
            }
            // Water nearby freezes over (it melts again like Frost Walker ice), and fire goes out.
            BlockPos centre = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-3, -2, -3), centre.offset(3, 1, 3))) {
                if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()
                        && level.getBlockState(pos.above()).isAir() && pos.distToCenterSqr(at) <= 12) {
                    level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                    level.scheduleTick(pos, Blocks.FROSTED_ICE, 60 + level.getRandom().nextInt(60));
                }
            }
            extinguish(level, at, radius());
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            level.sendParticles(TensuraParticleTypes.SNOWFLAKE.get(), at.x, at.y + 0.5, at.z, 40, 1.4, 0.6, 1.4, 0.02);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.3, at.z, 30, 1.2, 0.4, 1.2, 0.05);
            sound(level, at, TensuraSoundEvents.CAST_ICE.get());
        }
    },
    WIND("wind", 0xFFD8F5D0, 20, 4.0, 2) {
        @Override
        void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
            for (LivingEntity target : enemies(level, owner, at, radius())) {
                target.hurt(magic(level, talisman, owner), 2.0F * potency);
                Vec3 push = target.position().subtract(at).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : push.normalize();
                target.setDeltaMovement(push.x * 1.8 * potency, 0.55 * potency, push.z * 1.8 * potency);
                target.hurtMarked = true;
            }
            // The gust throws back projectiles that aren't yours...
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(radius()),
                    p -> p.isAlive() && !isAlly(p.getOwner() == null ? p : p.getOwner(), owner))) {
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.2));
                projectile.hurtMarked = true;
            }
            // ...and gives you a lift if you're caught in it: throw it at your feet to jump.
            if (owner instanceof ServerPlayer player && player.position().distanceTo(at) <= radius()) {
                player.setDeltaMovement(player.getDeltaMovement().x, 1.0 * potency, player.getDeltaMovement().z);
                player.hurtMarked = true;
                player.resetFallDistance();
            }
        }

        @Override
        void visuals(ServerLevel level, Vec3 at) {
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3, at.z, 30, 1.6, 0.4, 1.6, 0.15);
            sound(level, at, TensuraSoundEvents.CAST_WIND.get());
        }
    };

    /** Spells that count as an element for a rainbow talisman's combined damage. */
    private static final Spell[] ELEMENTS = {FIRE, WATER, WOOD, LIGHTNING, EARTH, ICE, WIND};
    private static final int[] RAINBOW = {0xFF3030, 0xFF9A2E, 0xFFE84A, 0x4AE05A, 0x3AB0FF, 0x5A5AFF, 0xC45AFF};

    private final String id;
    private final int colour;
    private final double magiculeCost;
    private final double radius;
    private final float elementDamage;

    Spell(String id, int colour, double magiculeCost, double radius, float elementDamage) {
        this.id = id;
        this.colour = colour;
        this.magiculeCost = magiculeCost;
        this.radius = radius;
        this.elementDamage = elementDamage;
    }

    /** Lower-case name, used for textures and translation keys. */
    public String id() {
        return id;
    }

    /** ARGB colour of this talisman's ink and seal. */
    public int colour() {
        return colour;
    }

    double radius() {
        return radius;
    }

    public double magiculeCost(boolean rainbow) {
        return magiculeCost * Config.SPELL_COST_MULTIPLIER.get() * (rainbow ? Config.RAINBOW_COST_MULTIPLIER.get() : 1);
    }

    public Spell next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Spell byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }

    /** What the spell does. */
    abstract void effect(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency);

    /** How the spell looks and sounds. */
    abstract void visuals(ServerLevel level, Vec3 at);

    /** Sets the spell off at {@code at}. */
    public void cast(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
        effect(level, talisman, owner, at, potency);
        visuals(level, at);
    }

    /**
     * The rainbow version: the same spell (same shape and mechanics) drawn in rainbow colours, and every enemy it
     * reaches also takes every element's damage at once, burning, freezing and slowing them together.
     */
    public void castRainbow(ServerLevel level, Entity talisman, @Nullable Entity owner, Vec3 at, float potency) {
        effect(level, talisman, owner, at, potency);
        float prism = 0;
        for (Spell element : ELEMENTS) {
            prism += element.elementDamage;
        }
        prism *= Config.RAINBOW_DAMAGE_MULTIPLIER.get().floatValue() * potency;
        for (LivingEntity target : enemies(level, owner, at, Math.max(radius, 3.0))) {
            target.invulnerableTime = 0; // the spell's own hit shouldn't swallow the prism damage
            target.hurt(magic(level, talisman, owner), prism);
            target.igniteForSeconds(3 * potency);
            freeze(target, potency * 0.6F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(60 * potency), 1));
        }
        rainbowVisuals(level, at, Math.max(radius, 3.0));
    }

    private static void rainbowVisuals(ServerLevel level, Vec3 at, double radius) {
        for (int ring = 0; ring < RAINBOW.length; ring++) {
            int rgb = RAINBOW[ring];
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), 1.6F);
            double r = radius * (ring + 1) / RAINBOW.length;
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2 * i / 16 + ring * 0.3;
                level.sendParticles(dust, at.x + Math.cos(angle) * r, at.y + 0.2 + ring * 0.15, at.z + Math.sin(angle) * r,
                        1, 0, 0.05, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 25, radius / 3, 0.6, radius / 3, 0.05);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessWave(0.8F, (float) radius), at.x, at.y + 0.1, at.z);
        sound(level, at, TensuraSoundEvents.CAST_LIGHT.get());
    }

    private static void freeze(LivingEntity target, float potency) {
        if (target.canFreeze()) {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + Math.round(160 * potency)));
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(60 * potency), 2));
    }

    private static void extinguish(ServerLevel level, Vec3 at, double radius) {
        for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(radius), Entity::isOnFire)) {
            entity.clearFire();
        }
        BlockPos centre = BlockPos.containing(at);
        int r = (int) Math.ceil(radius) - 1;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -2, -r), centre.offset(r, 2, r))) {
            if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
            }
        }
    }

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
