package com.tensurafragments.spirit;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.shikigami.Spell;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Spirit Control: every attack you make calls the next spirit in line (Ifrit, Sylphide, Undine, War Gnome, Blade
 * Tiger), which does one of its attacks at your target and vanishes. Triggered by your melee hits (while Spirit Link
 * is on) and by the skill key, which sends a spirit at whatever you're aiming at.
 */
public final class SpiritControl {
    /** Game time until which no new spirit can be called, per player. */
    private static final Map<UUID, Long> NEXT_SPIRIT = new HashMap<>();

    private SpiritControl() {
    }

    public static SpiritKind nextKind(ServerPlayer player) {
        return SpiritKind.byIndex(player.getData(ModRegistries.SPIRIT_INDEX));
    }

    /** Your melee hit calls a spirit onto the same target. */
    public static boolean onMeleeHit(ServerPlayer player, LivingEntity target) {
        if (!hasSkill(player) || !player.getData(ModRegistries.SPIRIT_LINK) || Spell.isAlly(target, player)) {
            return false;
        }
        return call(player, target, target.getBoundingBox().getCenter());
    }

    /** The skill key: a spirit at whatever you're aiming at (a creature, or the spot you point to). */
    public static boolean callAtAim(ServerPlayer player) {
        double range = Config.SPIRIT_RANGE.get();
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
            return call(player, target, target.getBoundingBox().getCenter());
        }
        return call(player, null, end);
    }

    /** Calls the next spirit to attack {@code target} or the point {@code aim}. */
    public static boolean call(ServerPlayer player, @Nullable LivingEntity target, Vec3 aim) {
        long now = player.level().getGameTime();
        if (now < NEXT_SPIRIT.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
            return false;
        }
        if (!Magicules.trySpend(player, Config.SPIRIT_MAGICULE_COST.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        NEXT_SPIRIT.put(player.getUUID(), now + Config.SPIRIT_INTERVAL_TICKS.get());
        SpiritKind kind = nextKind(player);
        player.setData(ModRegistries.SPIRIT_INDEX, kind.next().ordinal());
        player.level().addFreshEntity(SpiritEntity.create(player, kind, spawnPoint(player, kind, aim), target, aim));
        return true;
    }

    /** Melee spirits appear by the target (the tiger a few blocks off, to pounce); the others beside you. */
    private static Vec3 spawnPoint(ServerPlayer player, SpiritKind kind, Vec3 aim) {
        Vec3 toAim = aim.subtract(player.position()).multiply(1, 0, 1);
        Vec3 dir = toAim.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : toAim.normalize();
        Vec3 ground = new Vec3(aim.x, groundBelow(player, aim), aim.z);
        return switch (kind) {
            case WAR_GNOME -> ground.subtract(dir.scale(1.8));
            case BLADE_TIGER -> {
                double back = Math.min(4.0, Math.max(1.0, toAim.length() - 1.0));
                yield ground.subtract(dir.scale(back));
            }
            default -> {
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                boolean left = player.getData(ModRegistries.SPIRIT_INDEX) % 2 == 0;
                yield player.position().add(side.scale(left ? 1.5 : -1.5)).subtract(dir.scale(0.5));
            }
        };
    }

    private static double groundBelow(ServerPlayer player, Vec3 at) {
        Vec3 top = at.add(0, 1, 0);
        HitResult hit = player.level().clip(new ClipContext(top, top.add(0, -6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? at.y - 1 : hit.getLocation().y;
    }

    /** Spirit Link on/off: whether your melee hits call spirits. */
    public static void toggleLink(ServerPlayer player) {
        boolean on = !player.getData(ModRegistries.SPIRIT_LINK);
        player.setData(ModRegistries.SPIRIT_LINK, on);
        player.displayClientMessage(Component.translatable(on ? "tensurafragments.spirit.link_on" : "tensurafragments.spirit.link_off"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, on ? 1.6F : 0.9F);
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SPIRIT_CONTROL.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_SPIRIT_CONTROL.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.SPIRIT_CONTROL.get());
        }
    }
}
