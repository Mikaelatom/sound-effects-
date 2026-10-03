package com.tensurafragments.ally;

import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.shikigami.PaperBeastEntity;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.soul.SoulBond;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
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
        markNamed(entity);
    }

    /** Makes an already-named summon (by a name tag or Tensura's Naming) a companion for good. */
    public static void markNamed(Entity entity) {
        entity.setData(ModRegistries.NAMED_COMPANION, true);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    // ---- Naming ----

    /** How long after right-clicking with a blank name tag you have to type the name in chat. */
    private static final long NAMING_TICKS = 60 * 20;
    /** Players about to type a name: which creature, and when they asked. */
    private static final Map<UUID, PendingName> PENDING = new HashMap<>();

    private record PendingName(UUID entity, long time) {
    }

    /** Whether {@code player} may name {@code target}: it's their summon, or one of their ally's. */
    public static boolean mayName(ServerPlayer player, Entity target) {
        UUID owner = ownerOf(target);
        return owner != null && target.isAlive()
                && (owner.equals(player.getUUID()) || Alliances.areAllies(player.server, owner, player.getUUID()));
    }

    /**
     * A name tag on one of your own summons, or an ally's, whatever it is. It stays its summoner's. A tag already named
     * in an anvil names it right away; a blank one asks you to type the name in chat. Runs before the creature's own
     * handling.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();
        if (!stack.is(Items.NAME_TAG) || !(event.getEntity() instanceof ServerPlayer player) || !mayName(player, target)) {
            return;
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            applyName(player, target, stack.get(DataComponents.CUSTOM_NAME));
            stack.consume(1, player);
        } else {
            PENDING.put(player.getUUID(), new PendingName(target.getUUID(), player.serverLevel().getGameTime()));
            player.sendSystemMessage(Component.translatable("tensurafragments.companion.type_name", target.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** The name typed after right-clicking with a blank name tag (it isn't sent to chat). */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        PendingName pending = PENDING.remove(player.getUUID());
        if (pending == null || player.serverLevel().getGameTime() - pending.time() > NAMING_TICKS) {
            return;
        }
        event.setCanceled(true);
        String name = event.getRawText().trim();
        if (name.length() > 50) {
            name = name.substring(0, 50);
        }
        Entity target = find(player, pending.entity());
        if (name.isEmpty() || target == null || !mayName(player, target)) {
            player.sendSystemMessage(Component.translatable("tensurafragments.companion.name_failed").withStyle(ChatFormatting.RED));
            return;
        }
        if (!player.getAbilities().instabuild && !takeNameTag(player)) {
            player.sendSystemMessage(Component.translatable("tensurafragments.companion.no_tag").withStyle(ChatFormatting.RED));
            return;
        }
        applyName(player, target, Component.literal(name));
    }

    /** Uses up a blank-or-named name tag from the hands first, then the inventory. */
    private static boolean takeNameTag(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(Items.NAME_TAG)) {
                player.getItemInHand(hand).shrink(1);
                return true;
            }
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.NAME_TAG)) {
                inventory.getItem(i).shrink(1);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Entity find(ServerPlayer player, UUID id) {
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void applyName(ServerPlayer player, Entity target, Component name) {
        name(target, name);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
                    8, 0.3, 0.2, 0.3, 0);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1F, 1.2F);
        }
        player.sendSystemMessage(Component.translatable("tensurafragments.companion.named", target.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
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
                Vec3 at = beside(player, entity);
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

    /** Just behind you, or beside you, or where you stand: the first spot where it fits without being in a wall. */
    private static Vec3 beside(ServerPlayer owner, Entity entity) {
        for (float turn : new float[] {180, 90, -90, 0}) {
            Vec3 offset = Vec3.directionFromRotation(0, owner.getYRot() + turn).scale(1.5);
            Vec3 at = new Vec3(owner.getX() + offset.x, owner.getY(), owner.getZ() + offset.z);
            if (owner.level().noCollision(entity, entity.getDimensions(entity.getPose()).makeBoundingBox(at))) {
                return at;
            }
        }
        return owner.position();
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
        Vec3 at = beside(owner, entity);
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
        PENDING.remove(event.getEntity().getUUID());
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
