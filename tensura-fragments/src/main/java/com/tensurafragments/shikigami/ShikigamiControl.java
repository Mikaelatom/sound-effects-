package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.network.SyncSubstitutionPayload;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shikigami Control, modelled on Seika from <i>The Reincarnation of the Strongest Exorcist</i>. Paper from your
 * inventory is the ammo. Triggered through {@link ShikigamiControlSkill}.
 */
public final class ShikigamiControl {
    /** Game time until which the paper doll is ready, per player. */
    private static final Map<UUID, Long> SUBSTITUTION_WINDOW = new HashMap<>();
    /** Game time until which Substitution can't be used again, per player. */
    private static final Map<UUID, Long> SUBSTITUTION_COOLDOWN = new HashMap<>();

    private ShikigamiControl() {
    }

    // ---- Shikigami ---------------------------------------------------------------------------------------------

    /** Turns the block you're looking at into a shikigami. */
    public static boolean summon(ServerPlayer player) {
        BlockHitResult hit = lookedAtBlock(player, Config.SHIKIGAMI_REACH.get());
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_block"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (state.isAir() || hardness < 0 || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.bad_block"), true);
            return false;
        }
        if (!level.mayInteract(player, pos) || CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(),
                player, pos, state).isCanceled()) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.bad_block"), true);
            return false;
        }
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        double cost = Config.SHIKIGAMI_BASE_MAGICULE_COST.get()
                + Config.SHIKIGAMI_HARDNESS_MAGICULE_COST.get() * ShikigamiEntity.effectiveHardness(hardness);
        if (!Magicules.trySpend(player, cost)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }

        List<ShikigamiEntity> existing = shikigami(player);
        int overflow = existing.size() - Config.MAX_SHIKIGAMI.get() + 1;
        for (int i = 0; i < overflow; i++) {
            existing.get(i).revert(); // oldest first
        }

        Paper.consume(player);
        level.removeBlock(pos, false);
        ShikigamiEntity shikigami = ShikigamiEntity.create(player, state, hardness, pos);
        level.addFreshEntity(shikigami);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.6F, 1.5F),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, TensuraSoundEvents.CAST_EARTH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static void dismissAll(ServerPlayer player) {
        shikigami(player).forEach(ShikigamiEntity::revert);
    }

    /** The player's shikigami in their current dimension, oldest first. */
    public static List<ShikigamiEntity> shikigami(ServerPlayer player) {
        List<ShikigamiEntity> list = new ArrayList<>(player.serverLevel().getEntities(ModRegistries.SHIKIGAMI.get(),
                s -> s.isAlive() && player.getUUID().equals(s.getOwnerUUID())));
        list.sort(Comparator.comparingInt((ShikigamiEntity s) -> s.tickCount).reversed());
        return list;
    }

    // ---- Talisman ----------------------------------------------------------------------------------------------

    public static boolean throwTalisman(ServerPlayer player, Vec3 momentum) {
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        if (!Magicules.trySpend(player, Config.TALISMAN_MAGICULE_COST.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        Paper.consume(player);
        TalismanEntity talisman = TalismanEntity.create(player);
        talisman.setDeltaMovement(player.getLookAngle().scale(Config.TALISMAN_SPEED.get()).add(momentum));
        player.level().addFreshEntity(talisman);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    // ---- Barrier -----------------------------------------------------------------------------------------------

    /** Plants a talisman anchor where you're looking. Placing the last allowed anchor raises the barrier. */
    public static boolean placeAnchor(ServerPlayer player) {
        List<BarrierAnchorEntity> anchors = Barrier.anchors(player);
        if (anchors.stream().anyMatch(BarrierAnchorEntity::isActive)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.barrier_already_up"), true);
            return false;
        }
        int max = Config.MAX_BARRIER_ANCHORS.get();
        if (anchors.size() >= max) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.too_many_anchors", max), true);
            return false;
        }
        BlockHitResult hit = lookedAtBlock(player, Config.SHIKIGAMI_REACH.get() * 2);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_block"), true);
            return false;
        }
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        if (!Magicules.trySpend(player, Config.BARRIER_ANCHOR_MAGICULE_COST.get())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        Paper.consume(player);
        // Anchors always stand on top of the block you point at.
        BlockPos ground = hit.getBlockPos();
        Vec3 pos = new Vec3(hit.getLocation().x, ground.getY() + 1.0, hit.getLocation().z);
        int order = anchors.isEmpty() ? 0 : anchors.get(anchors.size() - 1).getOrder() + 1;
        player.level().addFreshEntity(BarrierAnchorEntity.create(player, pos, order));
        player.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        if (anchors.size() + 1 >= max) {
            Barrier.raise(player);
        }
        return true;
    }

    /** Raises the barrier if it's down (needs three anchors), or dispels it if it's up. */
    public static void toggleBarrier(ServerPlayer player) {
        if (Barrier.isUp(player)) {
            Barrier.dispel(player);
        } else {
            Barrier.raise(player);
        }
    }

    // ---- Substitution ------------------------------------------------------------------------------------------

    /** Readies a paper doll for a short window. Taking a hit in the window is blocked; missing it costs a cooldown. */
    public static boolean readySubstitution(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now < SUBSTITUTION_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.substitution_cooldown"), true);
            return false;
        }
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        int window = Config.SUBSTITUTION_WINDOW_TICKS.get();
        SUBSTITUTION_WINDOW.put(player.getUUID(), now + window);
        // Whiffing puts it on cooldown; a successful dodge clears this.
        SUBSTITUTION_COOLDOWN.put(player.getUUID(), now + window + Config.SUBSTITUTION_WHIFF_COOLDOWN_TICKS.get());
        sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.6F, 2.0F);
        return true;
    }

    /**
     * Called when the player is about to take damage. Returns true if a paper doll took the hit instead.
     */
    public static boolean trySubstitute(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !hasSkill(player) || !Paper.has(player)) {
            return false;
        }
        long now = player.level().getGameTime();
        UUID id = player.getUUID();
        boolean windowOpen = now <= SUBSTITUTION_WINDOW.getOrDefault(id, -1L);
        boolean auto = Config.AUTO_SUBSTITUTION.get() && now >= SUBSTITUTION_COOLDOWN.getOrDefault(id, 0L);
        if (!windowOpen && !auto) {
            return false;
        }

        Paper.consume(player);
        SUBSTITUTION_WINDOW.remove(id);
        SUBSTITUTION_COOLDOWN.put(id, auto && !windowOpen ? now + Config.AUTO_SUBSTITUTION_COOLDOWN_TICKS.get() : now);

        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                from.x, from.y + 1, from.z, 25, 0.3, 0.6, 0.3, 0.1);
        level.sendParticles(ParticleTypes.POOF, from.x, from.y + 1, from.z, 10, 0.3, 0.5, 0.3, 0.02);
        blinkAway(player, source);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.invulnerableTime = 20;
        sync(player);
        return true;
    }

    private static void blinkAway(ServerPlayer player, DamageSource source) {
        double distance = Config.SUBSTITUTION_BLINK_DISTANCE.get();
        if (distance <= 0) {
            return;
        }
        Vec3 threat = source.getSourcePosition();
        Vec3 away = threat == null ? player.getLookAngle().scale(-1) : player.position().subtract(threat);
        away = new Vec3(away.x, 0, away.z);
        away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().scale(-1).multiply(1, 0, 1) : away;
        if (away.lengthSqr() < 1.0E-4) {
            return;
        }
        away = away.normalize();
        for (double d = distance; d >= 0.5; d -= 0.5) {
            Vec3 target = player.position().add(away.scale(d));
            if (player.level().noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) {
                player.teleportTo(target.x, target.y, target.z);
                return;
            }
        }
    }

    public static boolean isSubstitutionReady(ServerPlayer player) {
        return player.level().getGameTime() <= SUBSTITUTION_WINDOW.getOrDefault(player.getUUID(), -1L);
    }

    private static void sync(ServerPlayer player) {
        long now = player.level().getGameTime();
        int window = (int) Math.max(0, SUBSTITUTION_WINDOW.getOrDefault(player.getUUID(), 0L) - now);
        int cooldown = (int) Math.max(0, SUBSTITUTION_COOLDOWN.getOrDefault(player.getUUID(), 0L) - now);
        PacketDistributor.sendToPlayer(player, new SyncSubstitutionPayload(window, cooldown));
    }

    // ---- Shared ------------------------------------------------------------------------------------------------

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SHIKIGAMI_CONTROL.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_SHIKIGAMI_CONTROL.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.SHIKIGAMI_CONTROL.get());
        }
    }

    private static BlockHitResult lookedAtBlock(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, (Entity) player));
    }
}
