package com.tensurafragments.shikigami;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.network.SyncShikigamiPayload;
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
    /** Game time until which Substitution can't trigger again, per player. */
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

        int max = Config.MAX_SHIKIGAMI.get();
        if (max > 0) {
            List<ShikigamiEntity> existing = shikigami(player);
            for (int i = 0; i < existing.size() - max + 1; i++) {
                existing.get(i).revert(); // oldest first
            }
        }

        Paper.Talisman paper = Paper.consume(player);
        level.removeBlock(pos, false);
        ShikigamiEntity shikigami = ShikigamiEntity.create(player, state, hardness, pos, paper.potency());
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

    public static Spell selectedSpell(ServerPlayer player) {
        return Spell.byIndex(player.getData(ModRegistries.SELECTED_SPELL));
    }

    /** Switches to the next spell talisman. */
    public static void cycleSpell(ServerPlayer player) {
        Spell spell = selectedSpell(player).next();
        player.setData(ModRegistries.SELECTED_SPELL, spell.ordinal());
        player.displayClientMessage(Component.translatable("tensurafragments.shikigami.spell_selected",
                Component.translatable("tensurafragments.spell." + spell.id())).withColor(spell.colour() & 0xFFFFFF), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        sync(player);
    }

    /** Throws the selected spell talisman. It goes off on contact with the ground or a creature. */
    public static boolean throwTalisman(ServerPlayer player, Vec3 momentum) {
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        Spell spell = selectedSpell(player);
        if (!Magicules.trySpend(player, spell.magiculeCost())) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }
        TalismanEntity talisman = TalismanEntity.create(player, Paper.consume(player), spell);
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
        Paper.Talisman paper = Paper.consume(player);
        // Anchors always stand on top of the block you point at.
        BlockPos ground = hit.getBlockPos();
        Vec3 pos = new Vec3(hit.getLocation().x, ground.getY() + 1.0, hit.getLocation().z);
        int order = anchors.isEmpty() ? 0 : anchors.get(anchors.size() - 1).getOrder() + 1;
        player.level().addFreshEntity(BarrierAnchorEntity.create(player, pos, order, paper));
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

    /** Turns automatic Substitution on or off (it's on by default), so you can save your paper. */
    public static boolean toggleSubstitution(ServerPlayer player) {
        boolean enabled = !player.getData(ModRegistries.SUBSTITUTION_ENABLED);
        player.setData(ModRegistries.SUBSTITUTION_ENABLED, enabled);
        player.displayClientMessage(Component.translatable(enabled
                ? "tensurafragments.shikigami.substitution_on" : "tensurafragments.shikigami.substitution_off"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.6F, enabled ? 2.0F : 1.2F);
        sync(player);
        return false;
    }

    /**
     * Called when the player is about to be hit. While Substitution is on and they have paper, a paper doll takes the
     * hit and they blink away. Only real attacks count (something hit them, or a blast went off near them), so
     * burning, drowning, falling and the like don't eat paper. Returns how much of the damage still gets through:
     * 0 for a paper doll, more for a leaf doll, 1 if no doll was used.
     */
    public static float trySubstitute(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !hasSkill(player)
                || !player.getData(ModRegistries.SUBSTITUTION_ENABLED) || !Paper.has(player)
                || (source.getEntity() == null && source.getDirectEntity() == null && source.getSourcePosition() == null)) {
            return 1;
        }
        long now = player.level().getGameTime();
        UUID id = player.getUUID();
        if (now < SUBSTITUTION_COOLDOWN.getOrDefault(id, 0L)) {
            return 1;
        }

        Paper.Talisman doll = Paper.consume(player);
        SUBSTITUTION_COOLDOWN.put(id, now + Config.SUBSTITUTION_COOLDOWN_TICKS.get());

        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, doll.item()),
                from.x, from.y + 1, from.z, 25, 0.3, 0.6, 0.3, 0.1);
        level.sendParticles(ParticleTypes.POOF, from.x, from.y + 1, from.z, 10, 0.3, 0.5, 0.3, 0.02);
        blinkAway(player, source);
        level.playSound(null, from.x, from.y, from.z, doll.isLeaf() ? SoundEvents.AZALEA_LEAVES_BREAK : SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.invulnerableTime = 20;
        sync(player);
        return 1 - doll.potency();
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

    /** Sends the Substitution state and selected spells (normal and rainbow) to the client for the HUD. */
    public static void sync(ServerPlayer player) {
        int cooldown = (int) Math.max(0, SUBSTITUTION_COOLDOWN.getOrDefault(player.getUUID(), 0L) - player.level().getGameTime());
        PacketDistributor.sendToPlayer(player, new SyncShikigamiPayload(player.getData(ModRegistries.SUBSTITUTION_ENABLED),
                cooldown, player.getData(ModRegistries.SELECTED_SPELL), player.getData(ModRegistries.RAINBOW_SPELL),
                player.getData(ModRegistries.FIRE_SPELL), player.getData(ModRegistries.SELECTED_BEAST)));
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
