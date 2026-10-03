package com.tensurafragments.ally;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.shikigami.PaperBeastEntity;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.soul.SoulBond;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Named companions. Use a name tag on anything you summoned (a soul, a shikigami, a paper beast, a creature from your
 * grimoire, a creature your souls possessed) and it's yours for good: no timer, no recall or dismissal, and it stays
 * with you. It catches up when you get far away, follows you to other dimensions and through death, and leaves with
 * you when you log off (coming back when you do). Only being killed ends it.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class Companions {
    /** Farther than this from you (and not fighting), a companion catches up. */
    private static final double CATCH_UP_DISTANCE = 24;

    private Companions() {
    }

    /** Whose summon this is, or null if it isn't anyone's. */
    @Nullable
    public static UUID ownerOf(Entity entity) {
        if (entity instanceof ShikigamiEntity || entity instanceof PaperBeastEntity) {
            return ((TamableAnimal) entity).getOwnerUUID();
        }
        SoulBond bond = SoulBond.get(entity);
        if (bond != null) {
            return bond.owner();
        }
        Binding binding = Binding.get(entity);
        return binding != null ? binding.binder() : null;
    }

    public static boolean isNamed(Entity entity) {
        return entity.getData(ModRegistries.NAMED_COMPANION);
    }

    /** Names a summon and makes it a companion for good. */
    public static void name(Entity entity, Component name) {
        entity.setCustomName(name);
        entity.setData(ModRegistries.NAMED_COMPANION, true);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    // ---- Naming ----

    /**
     * A name tag on one of your own summons, or an ally's, whatever it is. It stays its summoner's. Runs before the
     * creature's own handling.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();
        if (!stack.is(Items.NAME_TAG) || !stack.has(DataComponents.CUSTOM_NAME)
                || !(event.getEntity() instanceof ServerPlayer player) || !target.isAlive()) {
            return;
        }
        UUID owner = ownerOf(target);
        if (owner == null || !(owner.equals(player.getUUID()) || Alliances.areAllies(player.server, owner, player.getUUID()))) {
            return;
        }
        name(target, stack.get(DataComponents.CUSTOM_NAME));
        stack.consume(1, player);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
                8, 0.3, 0.2, 0.3, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1F, 1.2F);
        player.displayClientMessage(Component.translatable("tensurafragments.companion.named", target.getDisplayName()), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    // ---- Staying with you ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            gather(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            gather(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            gather(player);
        }
    }

    /**
     * Brings your companions along: from other dimensions, and when they've fallen far behind (unless they're in a
     * fight, or it's the paper beast you're seeing through). While you're dead they wait where they are.
     */
    public static void gather(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        for (Entity entity : companions(player)) {
            if (entity.level() != player.level()) {
                bring(entity, player);
            } else if (!(entity instanceof PaperBeastEntity beast && beast.isControlled())
                    && entity.distanceTo(player) > CATCH_UP_DISTANCE && !fighting(entity, player)) {
                Vec3 at = beside(player);
                entity.teleportTo(at.x, at.y, at.z);
                entity.resetFallDistance();
                if (entity instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
        }
    }

    private static boolean fighting(Entity entity, ServerPlayer owner) {
        return entity instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(owner) < CATCH_UP_DISTANCE * 2;
    }

    private static Vec3 beside(ServerPlayer owner) {
        Vec3 back = Vec3.directionFromRotation(0, owner.getYRot()).scale(-1.5);
        return new Vec3(owner.getX() + back.x, owner.getY(), owner.getZ() + back.z);
    }

    /** Moves a companion to its owner, wherever they are. */
    private static void bring(Entity entity, ServerPlayer owner) {
        CompoundTag tag = pack(entity);
        entity.discard();
        unpack(tag, owner);
    }

    private static CompoundTag pack(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putString("id", EntityType.getKey(entity.getType()).toString());
        return tag;
    }

    @Nullable
    private static Entity unpack(CompoundTag tag, ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        Entity entity = EntityType.create(tag, level).orElse(null);
        if (entity == null) {
            return null;
        }
        Vec3 at = beside(owner);
        entity.moveTo(at.x, at.y, at.z, owner.getYRot(), 0);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(owner.getYRot());
        }
        if (!level.addFreshEntity(entity)) {
            // Its old self is still around somewhere: come back as a new one.
            entity.setUUID(UUID.randomUUID());
            level.addFreshEntity(entity);
        }
        return entity;
    }

    /** Every named companion of this player, in every dimension. */
    public static List<Entity> companions(ServerPlayer player) {
        List<Entity> list = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.isAlive() && isNamed(entity) && player.getUUID().equals(ownerOf(entity))) {
                    list.add(entity);
                }
            }
        }
        return list;
    }

    /** Your companions leave with you... */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<CompoundTag> away = new ArrayList<>(player.getData(ModRegistries.AWAY_COMPANIONS));
        for (Entity entity : companions(player)) {
            away.add(pack(entity));
            entity.discard();
        }
        player.setData(ModRegistries.AWAY_COMPANIONS, away);
    }

    /** ...and come back with you. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<CompoundTag> away = player.getData(ModRegistries.AWAY_COMPANIONS);
        if (away.isEmpty()) {
            return;
        }
        player.setData(ModRegistries.AWAY_COMPANIONS, List.of());
        for (CompoundTag tag : away) {
            unpack(tag, player);
        }
    }
}
