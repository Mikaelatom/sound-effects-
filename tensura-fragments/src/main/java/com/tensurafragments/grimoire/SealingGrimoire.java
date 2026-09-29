package com.tensurafragments.grimoire;

import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.shikigami.Paper;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.skill.Magicules;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sealing Grimoire: trap weakened creatures and incoming magic in a book with paper talismans, then release them.
 * Released creatures fight for you for a while and then return to the book. Triggered through
 * {@link SealingGrimoireSkill}.
 */
public final class SealingGrimoire {
    /** Entities that can never be sealed (bosses and the like). */
    public static final TagKey<EntityType<?>> UNSEALABLE =
            TagKey.create(Registries.ENTITY_TYPE, TensuraFragments.id("unsealable"));

    private static final Map<UUID, Long> CATCH_WINDOW = new HashMap<>();
    private static final Map<UUID, Long> CATCH_COOLDOWN = new HashMap<>();

    private SealingGrimoire() {
    }

    // ---- The book ----------------------------------------------------------------------------------------------

    /**
     * The player's grimoire. If they don't have one yet, one ordinary book from their inventory becomes it.
     * Returns null (and tells them) if there's neither.
     */
    @Nullable
    public static ItemStack grimoire(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModRegistries.SEALING_GRIMOIRE.get())) {
                return inventory.getItem(i);
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.BOOK)) {
                stack.shrink(1);
                ItemStack grimoire = new ItemStack(ModRegistries.SEALING_GRIMOIRE.get());
                if (!inventory.add(grimoire)) {
                    player.drop(grimoire, false);
                    return null;
                }
                player.displayClientMessage(Component.translatable("tensurafragments.grimoire.created"), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), TensuraSoundEvents.GENERIC_CAST.get(),
                        SoundSource.PLAYERS, 0.8F, 1.2F);
                // Find it again: add() may have merged or moved it.
                return grimoire(player);
            }
        }
        player.displayClientMessage(Component.translatable("tensurafragments.grimoire.need_book"), true);
        return null;
    }

    private static boolean hasRoom(ServerPlayer player, ItemStack grimoire) {
        if (SealingGrimoireItem.contents(grimoire).pages().size() >= Config.GRIMOIRE_PAGES.get()) {
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.full"), true);
            return false;
        }
        return true;
    }

    // ---- Seal Creature -----------------------------------------------------------------------------------------

    public static boolean sealCreature(ServerPlayer player) {
        ItemStack grimoire = grimoire(player);
        if (grimoire == null) {
            return false;
        }
        LivingEntity target = lookedAtCreature(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.no_target"), true);
            return false;
        }
        // Your own released creature goes straight back in, free.
        if (Binding.isBoundTo(target, player)) {
            return reseal(target, player);
        }
        if (!hasRoom(player, grimoire)) {
            return false;
        }
        if (target.getType().is(UNSEALABLE) || target.getMaxHealth() > Config.SEAL_MAX_HEALTH.get()
                || target instanceof ShikigamiEntity
                || (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null)
                || Binding.get(target) != null) {
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.cannot_seal"), true);
            return false;
        }
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        double cost = Config.SEAL_BASE_MAGICULE_COST.get() + Config.SEAL_MAGICULE_COST_PER_HEALTH.get() * target.getMaxHealth();
        if (!Magicules.has(player, cost)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }

        // The talisman is spent either way: a failed seal burns it.
        Paper.Talisman talisman = Paper.consume(player);
        double threshold = Config.SEAL_HEALTH_THRESHOLD.get() * talisman.potency();
        ServerLevel level = player.serverLevel();
        if (target.getHealth() / target.getMaxHealth() > threshold) {
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    12, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, target, TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.too_strong",
                    Math.round(threshold * 100)), true);
            return false;
        }
        Magicules.trySpend(player, cost);
        seal(player, grimoire, target, GrimoireContents.Kind.CREATURE, 1.0F);
        return true;
    }

    @Nullable
    private static LivingEntity lookedAtCreature(ServerPlayer player) {
        double reach = Config.SEAL_REACH.get();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive(), reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---- Seal Magic --------------------------------------------------------------------------------------------

    /** Opens the book for a moment. The first spell or projectile that comes close in that window is caught. */
    public static boolean readyCatch(ServerPlayer player) {
        ItemStack grimoire = grimoire(player);
        if (grimoire == null || !hasRoom(player, grimoire)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now < CATCH_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.catch_cooldown"), true);
            return false;
        }
        if (!Paper.has(player)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_paper"), true);
            return false;
        }
        int window = Config.CATCH_WINDOW_TICKS.get();
        CATCH_WINDOW.put(player.getUUID(), now + window);
        CATCH_COOLDOWN.put(player.getUUID(), now + window + Config.CATCH_WHIFF_COOLDOWN_TICKS.get());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
        return true;
    }

    public static boolean isCatchOpen(ServerPlayer player) {
        return player.level().getGameTime() <= CATCH_WINDOW.getOrDefault(player.getUUID(), -1L);
    }

    /** Called every tick for every player. Catches an incoming projectile while the window is open. */
    public static void tickCatch(ServerPlayer player) {
        if (!isCatchOpen(player)) {
            return;
        }
        double radius = Config.CATCH_RADIUS.get();
        Vec3 centre = player.position().add(0, player.getBbHeight() / 2, 0);
        for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(radius), p -> p.isAlive() && !isFriendly(p.getOwner(), player))) {
            if (projectile.position().distanceTo(centre) > radius + 1) {
                continue;
            }
            ItemStack grimoire = grimoire(player);
            if (grimoire == null || !hasRoom(player, grimoire) || !Paper.has(player)) {
                return;
            }
            Paper.consume(player);
            seal(player, grimoire, projectile, GrimoireContents.Kind.MAGIC, (float) projectile.getDeltaMovement().length());
            // A good catch closes the book and it's ready again straight away.
            CATCH_WINDOW.remove(player.getUUID());
            CATCH_COOLDOWN.put(player.getUUID(), player.level().getGameTime());
            return;
        }
    }

    // ---- Release -----------------------------------------------------------------------------------------------

    public static boolean release(ServerPlayer player) {
        ItemStack grimoire = grimoire(player);
        if (grimoire == null) {
            return false;
        }
        GrimoireContents contents = SealingGrimoireItem.contents(grimoire);
        GrimoireContents.Page page = contents.selectedPage();
        if (page == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.empty"), true);
            return false;
        }
        double cost = page.kind() == GrimoireContents.Kind.CREATURE
                ? Config.RELEASE_CREATURE_MAGICULE_COST.get() : Config.RELEASE_MAGIC_MAGICULE_COST.get();
        if (!Magicules.trySpend(player, cost)) {
            player.displayClientMessage(Component.translatable("tensurafragments.shikigami.no_magicules"), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Entity entity = EntityType.loadEntityRecursive(page.data().copy(), level, e -> e);
        SealingGrimoireItem.setContents(grimoire, contents.withRemoved(contents.selected()));
        if (entity == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.grimoire.page_lost", page.name()), true);
            return false;
        }

        if (page.kind() == GrimoireContents.Kind.MAGIC) {
            Vec3 from = player.getEyePosition().add(look.scale(1.2));
            entity.moveTo(from.x, from.y, from.z, player.getYRot(), player.getXRot());
            if (entity instanceof Projectile projectile) {
                projectile.setOwner(player);
            }
            entity.setDeltaMovement(look.scale(Math.max(0.6, page.speed())));
            level.addFreshEntity(entity);
        } else {
            Vec3 at = releaseSpot(player, entity);
            entity.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
            if (entity instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth()); // it recovers inside the book
            }
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.setTarget(null);
            }
            Binding.bind(entity, player, level.getGameTime() + Config.BOUND_DURATION_TICKS.get());
            level.addFreshEntity(entity);
        }
        Vec3 fx = entity.position().add(0, entity.getBbHeight() / 2, 0);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessReversedWave(0.6F, 1.5F), fx.x, fx.y, fx.z);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), fx.x, fx.y, fx.z,
                16, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, fx.x, fx.y, fx.z, TensuraSoundEvents.GENERIC_CAST.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static Vec3 releaseSpot(ServerPlayer player, Entity entity) {
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1);
        forward = forward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : forward.normalize();
        for (double d = 2.5; d >= 0; d -= 0.5) {
            Vec3 spot = player.position().add(forward.scale(d));
            if (player.level().noCollision(entity, entity.getType().getDimensions().makeBoundingBox(spot))) {
                return spot;
            }
        }
        return player.position();
    }

    public static void cyclePage(ServerPlayer player) {
        ItemStack grimoire = grimoire(player);
        if (grimoire == null) {
            return;
        }
        GrimoireContents contents = SealingGrimoireItem.contents(grimoire).withNextSelected();
        SealingGrimoireItem.setContents(grimoire, contents);
        GrimoireContents.Page page = contents.selectedPage();
        player.displayClientMessage(page == null ? Component.translatable("tensurafragments.grimoire.empty")
                : Component.translatable("tensurafragments.grimoire.selected", contents.selected() + 1, page.name()), true);
    }

    // ---- Shared ------------------------------------------------------------------------------------------------

    /** Puts the entity into the grimoire as a page and removes it from the world. */
    private static void seal(ServerPlayer player, ItemStack grimoire, Entity entity, GrimoireContents.Kind kind, float speed) {
        String name = entity.getDisplayName().getString();
        entity.ejectPassengers();
        Binding.unbind(entity);
        CompoundTag data = new CompoundTag();
        entity.saveAsPassenger(data);
        data.remove("UUID");
        data.remove("Owner");
        SealingGrimoireItem.setContents(grimoire, SealingGrimoireItem.contents(grimoire)
                .withAdded(new GrimoireContents.Page(kind, name, data, speed)));

        ServerLevel level = player.serverLevel();
        Vec3 fx = entity.position().add(0, entity.getBbHeight() / 2, 0);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), fx.x, fx.y, fx.z,
                20, 0.3, 0.4, 0.3, 0.1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, fx.x, fx.y, fx.z, 20, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, fx.x, fx.y, fx.z, TensuraSoundEvents.CAST_DARK.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("tensurafragments.grimoire.sealed", name), true);
        entity.discard();
    }

    /** Sends a released creature back into its binder's grimoire. Returns false if it couldn't go back. */
    static boolean reseal(Entity entity, ServerPlayer binder) {
        ItemStack grimoire = grimoire(binder);
        if (grimoire == null || SealingGrimoireItem.contents(grimoire).pages().size() >= Config.GRIMOIRE_PAGES.get()) {
            return false;
        }
        seal(binder, grimoire, entity, GrimoireContents.Kind.CREATURE, 1.0F);
        return true;
    }

    static boolean isFriendly(@Nullable Entity entity, ServerPlayer player) {
        return entity != null && (entity == player
                || (entity instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID()))
                || Binding.isBoundTo(entity, player));
    }

    public static boolean hasSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SEALING_GRIMOIRE.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_SEALING_GRIMOIRE.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.SEALING_GRIMOIRE.get());
        }
    }
}
