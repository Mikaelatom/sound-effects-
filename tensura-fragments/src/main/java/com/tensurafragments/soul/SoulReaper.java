package com.tensurafragments.soul;

import com.mojang.authlib.properties.PropertyMap;
import com.tensurafragments.Config;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.network.SoulEntityPayload;
import com.tensurafragments.network.SyncSoulsPayload;
import com.tensurafragments.skill.ModSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Soul Reaper: every kill gives you souls (Tensura's soul points, Demon Lord Seed or not) and captures the soul of what
 * died. Each captured soul can be used once: summon it back as a ghost that fights for you, absorb it to take all its
 * EP, or send it into a creature to possess it (it becomes yours and grows stronger with every soul). Using a soul
 * takes it off your list and its worth off your soul count; recalling a summoned soul gives both back. Triggered
 * through {@link SoulReaperSkill}.
 */
public final class SoulReaper {
    /** Soul blue, for messages, names and the HUD. */
    public static final int SOUL_COLOUR = 0x7FE8FF;
    private static final ResourceLocation POSSESSION_MODIFIER = TensuraFragments.id("soul_possession");
    /** What each player's HUD was last sent, so it's only re-sent when something changed. */
    private static final Map<UUID, Integer> LAST_SYNC = new HashMap<>();

    private SoulReaper() {
    }

    // ---- Souls ----

    public static int soulPoints(Player player) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        return existence == null ? 0 : existence.getSoulPoints();
    }

    private static void addSoulPoints(Player player, long amount) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        if (existence != null) {
            existence.setSoulPoints((int) Math.max(0, Math.min(Integer.MAX_VALUE, existence.getSoulPoints() + amount)));
            existence.markDirty();
        }
    }

    /** Soul points shown the way Tensura's menu shows souls (1000 points = 1 soul). */
    public static String format(long points) {
        return String.format(Locale.ROOT, "%.1f", points / 1000.0);
    }

    public static List<CapturedSoul> souls(Player player) {
        return player.getData(ModRegistries.SOULS);
    }

    @Nullable
    public static CapturedSoul selected(Player player) {
        List<CapturedSoul> souls = souls(player);
        if (souls.isEmpty()) {
            return null;
        }
        return souls.get(Math.floorMod(player.getData(ModRegistries.SELECTED_SOUL), souls.size()));
    }

    /** Removes and returns the selected soul. */
    @Nullable
    private static CapturedSoul takeSelected(ServerPlayer player) {
        CapturedSoul soul = selected(player);
        if (soul == null) {
            return null;
        }
        List<CapturedSoul> souls = new ArrayList<>(souls(player));
        souls.remove(Math.floorMod(player.getData(ModRegistries.SELECTED_SOUL), souls.size()));
        player.setData(ModRegistries.SOULS, souls);
        if (!souls.isEmpty()) {
            player.setData(ModRegistries.SELECTED_SOUL, Math.floorMod(player.getData(ModRegistries.SELECTED_SOUL), souls.size()));
        }
        return soul;
    }

    /** Whose kill this was: the player, or the owner of the soul that did it. */
    @Nullable
    public static ServerPlayer reaper(@Nullable Entity killer) {
        if (killer instanceof ServerPlayer player) {
            return player;
        }
        SoulBond bond = killer == null ? null : SoulBond.get(killer);
        return bond != null && killer.level() instanceof ServerLevel level
                && level.getPlayerByUUID(bond.owner()) instanceof ServerPlayer owner ? owner : null;
    }

    /** Something died at a reaper's hand: souls go up and its soul is captured. */
    public static void onKill(ServerPlayer reaper, LivingEntity victim) {
        if (victim == reaper || !hasSkill(reaper) || victim.getType().is(TensuraEntityTags.SOUL_DROP_EXCLUDED)) {
            return;
        }
        SoulBond bond = SoulBond.get(victim);
        if (bond != null && bond.summoned()) {
            // A summoned soul has no soul of its own left to take.
            return;
        }
        double ep = Math.max(0, EnergyHelper.getMaxEP(victim));
        String type = victim instanceof Player ? CapturedSoul.PLAYER : BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        List<CapturedSoul> souls = new ArrayList<>(souls(reaper));
        CapturedSoul soul = new CapturedSoul(type, victim.getName().getString(), ep);
        souls.add(soul);
        addSoulPoints(reaper, soulValue(soul));
        while (souls.size() > Config.MAX_CAPTURED_SOULS.get()) {
            souls.remove(souls.stream().min(Comparator.comparingDouble(CapturedSoul::ep)).orElseThrow());
        }
        reaper.setData(ModRegistries.SOULS, souls);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                    8, 0.3, 0.4, 0.3, 0.04);
        }
        sync(reaper);
    }

    public static void cycle(ServerPlayer player) {
        List<CapturedSoul> souls = souls(player);
        if (souls.isEmpty()) {
            player.displayClientMessage(Component.translatable("tensurafragments.soul.none"), true);
            return;
        }
        player.setData(ModRegistries.SELECTED_SOUL, Math.floorMod(player.getData(ModRegistries.SELECTED_SOUL) + 1, souls.size()));
        CapturedSoul soul = selected(player);
        player.displayClientMessage(Component.translatable("tensurafragments.soul.selected", soul.name(), epText(soul.ep()),
                format(soulValue(soul))).withColor(SOUL_COLOUR), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_SAND_STEP,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        sync(player);
    }

    public static String epText(double ep) {
        return String.format(Locale.ROOT, "%,d", Math.round(ep));
    }

    /** What a soul is worth in soul points: what its kill gave you, and what using it takes away again. */
    public static int soulValue(CapturedSoul soul) {
        return (int) Math.min(Integer.MAX_VALUE,
                Config.SOUL_POINTS_PER_KILL.get() + Math.round(soul.ep() * Config.SOUL_POINTS_PER_EP.get()));
    }

    /** A soul was used: it's gone from your list, and its worth comes off your soul count. */
    private static void spend(ServerPlayer player, CapturedSoul soul) {
        addSoulPoints(player, -Math.min(soulPoints(player), soulValue(soul)));
    }

    // ---- Summon ----

    /** Your summoned souls that are out right now. */
    public static List<Mob> summons(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(128), mob -> {
            SoulBond bond = SoulBond.get(mob);
            return bond != null && bond.summoned() && bond.owner().equals(player.getUUID()) && mob.isAlive();
        });
    }

    public static boolean summon(ServerPlayer player) {
        CapturedSoul soul = selected(player);
        if (soul == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.soul.none"), true);
            return false;
        }
        if (summons(player).size() >= Config.MAX_SOUL_SUMMONS.get()) {
            fail(player, Component.translatable("tensurafragments.soul.too_many", Config.MAX_SOUL_SUMMONS.get()));
            return false;
        }
        ServerLevel level = player.serverLevel();
        Mob mob = createBody(level, soul);
        if (mob == null) {
            fail(player, Component.translatable("tensurafragments.soul.cant_summon", soul.name()));
            return false;
        }
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot()).scale(2);
        mob.moveTo(player.getX() + forward.x, player.getY(), player.getZ() + forward.z, player.getYRot(), 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        if (soul.isPlayer()) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(Optional.of(soul.name()), Optional.empty(), new PropertyMap()));
            mob.setItemSlot(EquipmentSlot.HEAD, head);
            mob.setDropChance(EquipmentSlot.HEAD, 0);
        }
        mob.setCustomName(Component.translatable("tensurafragments.soul.summon_name", soul.name()).withColor(SOUL_COLOUR));
        mob.setPersistenceRequired();
        mob.setData(ModRegistries.SOUL_BOND, new SoulBond(player.getUUID(),
                level.getGameTime() + Config.SOUL_SUMMON_SECONDS.get() * 20L, true, 0, soul));
        level.addFreshEntity(mob);
        announce(mob);
        // The soul is spent: one kill, one summon.
        takeSelected(player);
        spend(player, soul);
        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 0.2, mob.getZ(), 12, 0.5, 0.1, 0.5, 0.02);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 0.7F);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.6F);
        sync(player);
        return true;
    }

    /** A body for the soul to come back in: what it was (a player's soul comes back as a zombie wearing their head). */
    @Nullable
    private static Mob createBody(ServerLevel level, CapturedSoul soul) {
        EntityType<?> type = soul.isPlayer() ? EntityType.ZOMBIE : EntityType.byString(soul.type()).orElse(null);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level);
        if (entity instanceof Mob mob) {
            return mob;
        }
        if (entity != null) {
            entity.discard();
        }
        return null;
    }

    // ---- Recall ----

    /**
     * Calls every summoned soul back: each goes back on your list (worth and all) to be summoned again later. Souls
     * that possessed one of them are lost with it.
     */
    public static boolean recall(ServerPlayer player) {
        List<Mob> summons = summons(player);
        if (summons.isEmpty()) {
            fail(player, Component.translatable("tensurafragments.soul.none_out"));
            return false;
        }
        ServerLevel level = player.serverLevel();
        List<CapturedSoul> souls = new ArrayList<>(souls(player));
        for (Mob mob : summons) {
            SoulBond bond = SoulBond.get(mob);
            if (bond != null && bond.soul() != null) {
                souls.add(bond.soul());
                addSoulPoints(player, soulValue(bond.soul()));
            }
            Vec3 from = mob.position().add(0, mob.getBbHeight() / 2, 0);
            Vec3 to = player.position().add(0, 1, 0);
            for (int i = 0; i <= 10; i++) {
                Vec3 at = from.lerp(to, i / 10.0);
                level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
            }
            mob.discard();
        }
        while (souls.size() > Config.MAX_CAPTURED_SOULS.get()) {
            souls.remove(souls.stream().min(Comparator.comparingDouble(CapturedSoul::ep)).orElseThrow());
        }
        player.setData(ModRegistries.SOULS, souls);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 15, 0.4, 0.6, 0.4, 0.03);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 1.0F);
        player.displayClientMessage(Component.translatable("tensurafragments.soul.recalled", summons.size())
                .withColor(SOUL_COLOUR), true);
        sync(player);
        return true;
    }

    // ---- Absorb ----

    public static boolean absorb(ServerPlayer player) {
        CapturedSoul soul = takeSelected(player);
        if (soul == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.soul.none"), true);
            return false;
        }
        spend(player, soul);
        double gain = soul.ep() * Config.SOUL_ABSORB_RATE.get();
        if (gain > 0) {
            EnergyHelper.increaseMaxEP(player, gain);
        }
        player.displayClientMessage(Component.translatable("tensurafragments.soul.absorbed", soul.name(), epText(gain))
                .withColor(SOUL_COLOUR), true);
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getViewVector(1);
        for (int i = 0; i < 12; i++) {
            Vec3 from = player.getEyePosition().add(look.scale(1.5 + i * 0.15));
            level.sendParticles(ParticleTypes.SOUL, from.x, from.y - 0.3, from.z, 1, 0.2, 0.2, 0.2, 0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 1.4F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.6F);
        sync(player);
        return true;
    }

    // ---- Possess ----

    public static boolean possess(ServerPlayer player) {
        CapturedSoul soul = selected(player);
        if (soul == null) {
            player.displayClientMessage(Component.translatable("tensurafragments.soul.none"), true);
            return false;
        }
        LivingEntity looked = lookedAt(player, Config.SOUL_POSSESS_RANGE.get());
        if (!(looked instanceof Mob target)) {
            fail(player, Component.translatable("tensurafragments.soul.no_target"));
            return false;
        }
        SoulBond bond = SoulBond.get(target);
        boolean ours = bond != null && bond.owner().equals(player.getUUID());
        if (!ours && (bond != null || Binding.get(target) != null
                || (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                && !ownable.getOwnerUUID().equals(player.getUUID())))) {
            fail(player, Component.translatable("tensurafragments.soul.taken", target.getDisplayName()));
            return false;
        }
        int stacks = ours ? bond.stacks() : 0;
        if (stacks >= Config.SOUL_POSSESS_MAX_STACKS.get()) {
            fail(player, Component.translatable("tensurafragments.soul.full", target.getDisplayName()));
            return false;
        }
        double targetEp = EnergyHelper.getMaxEP(target);
        if (!ours && soul.ep() < targetEp) {
            // A soul can only take over something no stronger than it was.
            fail(player, Component.translatable("tensurafragments.soul.too_strong", target.getDisplayName(), epText(targetEp),
                    soul.name(), epText(soul.ep())));
            return false;
        }
        takeSelected(player);
        spend(player, soul);
        // Possessing one of your summoned souls keeps it here for good.
        SoulBond possessed = ours ? new SoulBond(bond.owner(), Long.MAX_VALUE, bond.summoned(), stacks + 1, bond.soul())
                : new SoulBond(player.getUUID(), Long.MAX_VALUE, false, 1, null);
        target.setData(ModRegistries.SOUL_BOND, possessed);
        applyPossession(target, possessed.stacks());
        if (soul.ep() > 0) {
            EnergyHelper.increaseMaxEP(target, soul.ep());
        }
        target.setHealth(target.getMaxHealth());
        target.setPersistenceRequired();
        if (isFriendly(target.getTarget(), player)) {
            target.setTarget(null);
        }
        announce(target);
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
        for (int i = 0; i <= 16; i++) {
            Vec3 at = from.lerp(to, i / 16.0);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 30, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS,
                0.5F, 1.5F);
        player.displayClientMessage(Component.translatable("tensurafragments.soul.possessed", soul.name(), target.getDisplayName(),
                possessed.stacks()).withColor(SOUL_COLOUR), true);
        sync(player);
        return true;
    }

    /** Every possessing soul adds to the creature's health, damage and speed. */
    public static void applyPossession(LivingEntity entity, int stacks) {
        boost(entity, Attributes.MAX_HEALTH, stacks * Config.SOUL_POSSESS_HEALTH.get());
        boost(entity, Attributes.ATTACK_DAMAGE, stacks * Config.SOUL_POSSESS_DAMAGE.get());
        boost(entity, Attributes.MOVEMENT_SPEED, stacks * Config.SOUL_POSSESS_SPEED.get());
    }

    private static void boost(LivingEntity entity, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(POSSESSION_MODIFIER, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    @Nullable
    private static LivingEntity lookedAt(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1);
        Vec3 end = eye.add(view.scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---- Shared ----

    /** Never turned on: you, your tamed animals, your souls and your grimoire's creatures. */
    public static boolean isFriendly(@Nullable Entity entity, ServerPlayer player) {
        return entity != null && (entity == player
                || (entity instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID()))
                || SoulBond.isBoundTo(entity, player)
                || Binding.isBoundTo(entity, player));
    }

    /** Tells everyone nearby how to draw it. */
    public static void announce(Entity entity) {
        SoulBond bond = SoulBond.get(entity);
        if (bond != null) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, new SoulEntityPayload(entity.getId(), bond.kind()));
        }
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_SOIL_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** Sends the HUD what it shows, if it changed. */
    public static void sync(ServerPlayer player) {
        int points = soulPoints(player);
        List<CapturedSoul> souls = souls(player);
        int selected = souls.isEmpty() ? 0 : Math.floorMod(player.getData(ModRegistries.SELECTED_SOUL), souls.size());
        int hash = Objects.hash(points, souls, selected);
        Integer last = LAST_SYNC.put(player.getUUID(), hash);
        if (last == null || last != hash) {
            PacketDistributor.sendToPlayer(player, new SyncSoulsPayload(points, List.copyOf(souls), selected));
        }
    }

    static void forget(Player player) {
        LAST_SYNC.remove(player.getUUID());
    }

    public static boolean hasSkill(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(ModSkills.SOUL_REAPER.getId()).isPresent();
    }

    public static void grantSkill(ServerPlayer player) {
        if (Config.GRANT_SOUL_REAPER.get() && !hasSkill(player)) {
            SkillHelper.learnSkill(player, ModSkills.SOUL_REAPER.get());
        }
    }
}
