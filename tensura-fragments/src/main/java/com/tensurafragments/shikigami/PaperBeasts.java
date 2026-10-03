package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.ally.Companions;
import com.tensurafragments.network.PossessPayload;
import com.tensurafragments.skill.Magicules;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Paper beasts: fold paper into an owl, hound, winged cat or horned rabbit, then possess one to see through its eyes
 * and steer it. Your body stays where it is, helpless: taking damage snaps you back.
 */
public final class PaperBeasts {
    /** Which beast each player is seeing through (entity id). */
    private static final Map<UUID, Integer> POSSESSED = new HashMap<>();
    /** Keeps the area around a possessing player's body loaded while the world is sent around the beast. */
    private static final TicketType<ChunkPos> BODY = TicketType.create("tensurafragments_body",
            Comparator.comparingLong(ChunkPos::toLong), 60);
    /** How far you can look to pick out the beast to possess. */
    private static final double PICK_RANGE = 256;

    private PaperBeasts() {
    }

    public static BeastKind selectedKind(ServerPlayer player) {
        return BeastKind.byIndex(player.getData(ModRegistries.SELECTED_BEAST));
    }

    public static void cycleKind(ServerPlayer player) {
        BeastKind kind = selectedKind(player).next();
        player.setData(ModRegistries.SELECTED_BEAST, kind.ordinal());
        player.displayClientMessage(Component.translatable("tensurafragments.beast.selected",
                Component.translatable("tensurafragments.beast." + kind.id()), kind.paper()), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.5F, 1.8F);
        ShikigamiControl.sync(player);
    }

    /** Folds the selected beast from paper, just in front of you. */
    public static boolean fold(ServerPlayer player) {
        BeastKind kind = selectedKind(player);
        if (!Paper.has(player, kind.paper())) {
            player.displayClientMessage(Component.translatable("tensurafragments.beast.no_paper", kind.paper()), true);
            return false;
        }
        if (!Magicules.trySpend(player, Config.PAPER_BEAST_MAGICULE_COST.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        float potency = Paper.consume(player, kind.paper());
        ServerLevel level = player.serverLevel();
        Vec3 at = spawnSpot(player, kind);
        PaperBeastEntity beast = PaperBeastEntity.create(player, kind, at, potency);
        level.addFreshEntity(beast);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                at.x, at.y + 0.4, at.z, 16, 0.3, 0.3, 0.3, 0.06);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, at.x, at.y, at.z, TensuraSoundEvents.CAST_WIND.get(), SoundSource.PLAYERS, 0.6F, 1.5F);
        return true;
    }

    private static Vec3 spawnSpot(ServerPlayer player, BeastKind kind) {
        Vec3 eye = player.getEyePosition();
        Vec3 flat = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 wanted = eye.add(flat.scale(1.5));
        Vec3 clipped = player.level().clip(new ClipContext(eye, wanted, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                player)).getLocation().subtract(flat.scale(0.3));
        return kind.flies() ? clipped : new Vec3(clipped.x, player.getY(), clipped.z);
    }

    /** The player's paper beasts in their current dimension, oldest first. */
    public static List<PaperBeastEntity> beasts(ServerPlayer player) {
        List<PaperBeastEntity> list = new ArrayList<>(player.serverLevel().getEntities(
                EntityTypeTest.forClass(PaperBeastEntity.class),
                b -> b.isAlive() && player.getUUID().equals(b.owner())));
        list.sort(Comparator.comparingInt((PaperBeastEntity b) -> b.tickCount).reversed());
        return list;
    }

    public static void dismissAll(ServerPlayer player) {
        release(player);
        beasts(player).stream().filter(beast -> !Companions.isNamed(beast)).forEach(PaperBeastEntity::unfold);
    }

    // ---- Possession ----------------------------------------------------------------------------------------------

    @Nullable
    public static PaperBeastEntity possessed(ServerPlayer player) {
        Integer id = POSSESSED.get(player.getUUID());
        return id != null && player.level().getEntity(id) instanceof PaperBeastEntity beast && beast.isAlive() ? beast : null;
    }

    /**
     * Sees through a paper beast: the one you're looking at, or else your nearest. Pressing again while you're
     * possessing one brings you back.
     */
    public static boolean togglePossession(ServerPlayer player) {
        if (POSSESSED.containsKey(player.getUUID())) {
            release(player);
            return false;
        }
        PaperBeastEntity beast = lookedAtBeast(player);
        if (beast == null) {
            beast = beasts(player).stream()
                    .min(Comparator.comparingDouble(b -> b.distanceToSqr(player))).orElse(null);
        }
        if (beast == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.beast.none"), true);
            return false;
        }
        possess(player, beast);
        return true;
    }

    public static void possess(ServerPlayer player, PaperBeastEntity beast) {
        release(player);
        POSSESSED.put(player.getUUID(), beast.getId());
        beast.setControlled(true);
        beast.steer(0, 0, false, false, player.getYRot(), player.getXRot());
        PacketDistributor.sendToPlayer(player, new PossessPayload(beast.getId()));
        refreshView(player);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), TensuraSoundEvents.CAST_LIGHT.get(),
                SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** Back to your own body. */
    public static void release(ServerPlayer player) {
        Integer id = POSSESSED.remove(player.getUUID());
        if (id == null) {
            return;
        }
        if (player.level().getEntity(id) instanceof PaperBeastEntity beast) {
            beast.setControlled(false);
        }
        PacketDistributor.sendToPlayer(player, new PossessPayload(-1));
        refreshView(player);
    }

    /**
     * What the world is loaded and sent around for this player: the beast they're seeing through, or themselves.
     * There's no range limit: wherever the beast goes, the world goes with it.
     */
    public static Entity viewpoint(ServerPlayer player) {
        if (POSSESSED.isEmpty()) {
            return player;
        }
        PaperBeastEntity beast = possessed(player);
        return beast != null && beast.level() == player.level() ? beast : player;
    }

    private static void refreshView(ServerPlayer player) {
        player.serverLevel().getChunkSource().move(player);
    }

    /** Clears state for a player leaving, without sending them anything. */
    public static void forget(ServerPlayer player) {
        Integer id = POSSESSED.remove(player.getUUID());
        if (id != null && player.level().getEntity(id) instanceof PaperBeastEntity beast) {
            beast.setControlled(false);
        }
    }

    /** Steering sent by the possessing player's client each tick. */
    public static void steer(ServerPlayer player, float forward, float strafe, boolean jump, boolean down, float yaw,
                             float pitch, boolean attack) {
        PaperBeastEntity beast = possessed(player);
        if (beast == null) {
            return;
        }
        beast.steer(forward, strafe, jump, down, yaw, pitch);
        if (attack) {
            beast.controlledAttack();
        }
    }

    /** Keeps the link alive: magicules every second, and it snaps if the beast is lost. Distance doesn't matter. */
    public static void tick(ServerPlayer player) {
        if (!POSSESSED.containsKey(player.getUUID())) {
            return;
        }
        PaperBeastEntity beast = possessed(player);
        if (beast == null || !player.isAlive() || beast.level() != player.level()) {
            release(player);
            return;
        }
        // The world follows the beast; your body's surroundings stay loaded so it can still be found (and hurt).
        refreshView(player);
        if (player.tickCount % 20 == 0) {
            player.serverLevel().getChunkSource().addRegionTicket(BODY, player.chunkPosition(), 3, player.chunkPosition());
        }
        if (player.tickCount % 20 == 0 && !Magicules.trySpend(player, Config.POSSESSION_MAGICULES_PER_SECOND.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            release(player);
            return;
        }
        if (beast.getKind() == BeastKind.OWL && player.tickCount % 20 == 0) {
            // Owl eyes: see in the dark.
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60, 0, false, false, false));
        }
    }

    /** Your body was hurt: the shock throws you back into it. */
    public static void onOwnerHurt(ServerPlayer player) {
        if (POSSESSED.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("tensurafragments.beast.snapped_back"), true);
            release(player);
        }
    }

    @Nullable
    private static PaperBeastEntity lookedAtBeast(ServerPlayer player) {
        double range = PICK_RANGE;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, area,
                e -> e instanceof PaperBeastEntity beast && player.getUUID().equals(beast.owner()));
        return hit != null ? (PaperBeastEntity) hit.getEntity() : null;
    }
}
