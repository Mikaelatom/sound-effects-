package com.tensurafragments.spirit;

import com.tensurafragments.Config;
import com.tensurafragments.network.RainbowEntityPayload;
import com.tensurafragments.rainbow.RainbowMagic;
import com.tensurafragments.shikigami.Spell;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.FireBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WaterBallProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WindBladeProjectile;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** What each spirit does in the moment it strikes. */
final class SpiritAttacks {
    private SpiritAttacks() {
    }

    static void strike(SpiritEntity spirit, ServerLevel level, ServerPlayer owner) {
        SpiritKind kind = spirit.getKind();
        float damage = Config.SPIRIT_DAMAGE.get().floatValue() * kind.damageMultiplier() * com.tensurafragments.skill.EpScaling.multiplier(owner);
        switch (kind) {
            case IFRIT -> {
                TensuraFlyingProjectile ball = new FireBallProjectile(level, owner);
                ball.setBurnTicks(100);
                throwMagic(spirit, level, ball, damage, 1.6F);
                level.playSound(null, spirit.getX(), spirit.getY(), spirit.getZ(), TensuraSoundEvents.CAST_FIRE.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            case SYLPHIDE -> {
                throwMagic(spirit, level, new WindBladeProjectile(level, owner), damage, 2.2F);
                level.playSound(null, spirit.getX(), spirit.getY(), spirit.getZ(), TensuraSoundEvents.CAST_WIND.get(),
                        SoundSource.PLAYERS, 1.0F, 1.1F);
            }
            case UNDINE -> {
                throwMagic(spirit, level, new WaterBallProjectile(level, owner), damage, 1.6F);
                level.playSound(null, spirit.getX(), spirit.getY(), spirit.getZ(), TensuraSoundEvents.CAST_WATER.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            case WAR_GNOME -> stomp(spirit, level, owner, damage);
            case BLADE_TIGER -> slash(spirit, level, owner, damage);
        }
    }

    /** Fires one of Tensura's own spell projectiles from the spirit's hand at its target. */
    private static void throwMagic(SpiritEntity spirit, ServerLevel level, TensuraFlyingProjectile projectile, float damage,
                                   float speed) {
        Vec3 hand = spirit.position().add(0, 1.4 * spirit.getKind().scale(), 0)
                .add(Vec3.directionFromRotation(0, spirit.getYRot()).scale(0.6));
        Vec3 dir = spirit.aim().subtract(hand);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = Vec3.directionFromRotation(0, spirit.getYRot());
        }
        projectile.setPos(hand.x, hand.y, hand.z);
        com.tensurafragments.skill.EpScaling.markScaled(projectile);
        projectile.setDamage(damage);
        projectile.setSpeed(speed);
        projectile.shoot(dir.x, dir.y, dir.z, speed, 0);
        // A rainbow spirit's magic is rainbow too: every element in one hit.
        boolean rainbow = RainbowMagic.isRainbow(spirit);
        if (rainbow) {
            RainbowMagic.mark(projectile);
        }
        level.addFreshEntity(projectile);
        if (rainbow) {
            PacketDistributor.sendToPlayersTrackingEntity(projectile, new RainbowEntityPayload(projectile.getId()));
        }
    }

    /** The ground under the target bursts and throws everything near it into the air. */
    private static void stomp(SpiritEntity spirit, ServerLevel level, ServerPlayer owner, float damage) {
        Vec3 at = spirit.aim();
        BlockPos ground = BlockPos.containing(at.x, spirit.getY() - 0.5, at.z);
        BlockState block = level.getBlockState(ground);
        if (!block.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), at.x, spirit.getY() + 0.2, at.z,
                    60, 1.5, 0.3, 1.5, 0.3);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, spirit.getY() + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, TensuraSoundEvents.CAST_EARTH.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
        DamageSource source = level.damageSources().indirectMagic(spirit, owner);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0, 2.0, 3.0),
                e -> e.isAlive() && !e.isSpectator() && !Spell.isAlly(e, owner))) {
            victim.invulnerableTime = 0;
            if (victim.hurt(source, damage)) {
                victim.push(0, 0.9, 0);
                victim.hurtMarked = true;
            }
        }
    }

    /** The Blade Tiger's pounce cuts everything along its path. */
    private static void slash(SpiritEntity spirit, ServerLevel level, ServerPlayer owner, float damage) {
        Vec3 from = spirit.start().add(0, 0.8, 0);
        Vec3 to = spirit.aim();
        Vec3 path = to.subtract(from);
        Vec3 end = path.lengthSqr() < 1.0E-4 ? to : to.add(path.normalize().scale(1.5));
        // Dealt by the tiger (for your credit), so a rainbow tiger's cut carries every element.
        DamageSource source = level.damageSources().source(DamageTypes.PLAYER_ATTACK, spirit, owner);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5),
                e -> e.isAlive() && !e.isSpectator() && !Spell.isAlly(e, owner))) {
            Vec3 centre = victim.getBoundingBox().getCenter();
            if (distanceToSegment(centre, from, end) > 1.5 + victim.getBbWidth() / 2) {
                continue;
            }
            victim.invulnerableTime = 0;
            if (victim.hurt(source, damage)) {
                Vec3 push = path.multiply(1, 0, 1);
                if (push.lengthSqr() > 1.0E-4) {
                    push = push.normalize().scale(0.6);
                    victim.push(push.x, 0.2, push.z);
                    victim.hurtMarked = true;
                }
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-9 ? 0 : Math.max(0, Math.min(1, point.subtract(a).dot(ab) / lengthSqr));
        return point.distanceTo(a.add(ab.scale(t)));
    }
}
