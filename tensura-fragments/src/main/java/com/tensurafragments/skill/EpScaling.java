package com.tensurafragments.skill;

import com.tensurafragments.Config;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.combat.CombatMode;
import com.tensurafragments.grimoire.Binding;
import com.tensurafragments.shikigami.PaperBeastEntity;
import com.tensurafragments.shikigami.ShikigamiEntity;
import com.tensurafragments.soul.SoulBond;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Damage that grows with EP: this addon's skills (and their summons), Tensura's own skills and magic when a player uses
 * them, and Combat Mode's punches hit harder the more EP whoever's behind them has. Each tenfold of EP above the base adds a fixed step to the multiplier (by default 100 EP
 * hits normally, 1,000 at 1.6x, 10,000 at 2.2x, 100,000 at 2.8x, a million at 3.4x), up to a cap.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class EpScaling {
    private EpScaling() {
    }

    /** The current EP of this creature or player (0 if it has none). */
    public static double ep(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return 0;
        }
        IExistence existence = TensuraStorages.getExistenceFrom(living);
        return existence == null ? 0 : Math.max(0, existence.getEP());
    }

    /** How much harder whoever this is hits, from their EP. 1 for nobody, or if scaling is off. */
    public static float multiplier(@Nullable Entity caster) {
        if (caster == null || !Config.EP_SCALING.get()) {
            return 1;
        }
        return multiplierFor(ep(caster));
    }

    public static float multiplierFor(double ep) {
        double base = Config.EP_SCALING_BASE.get();
        if (ep <= base) {
            return 1;
        }
        double steps = Math.log10(ep / base);
        return (float) Math.min(Config.EP_SCALING_MAX.get(), 1 + steps * Config.EP_SCALING_PER_TENFOLD.get());
    }

    /** Marks one of Tensura's projectiles fired by this addon's skills: its damage is already scaled. */
    private static final String SCALED_KEY = "tensurafragments_ep_scaled";

    public static <T extends Entity> T markScaled(T projectile) {
        projectile.getPersistentData().putBoolean(SCALED_KEY, true);
        return projectile;
    }

    /** Hurts the target with damage scaled by the caster's EP. */
    public static boolean hurt(@Nullable Entity caster, LivingEntity target, DamageSource source, float amount) {
        return target.hurt(source, amount * multiplier(caster));
    }

    /**
     * Combat Mode punches (and slams and throws), and the melee hits of this addon's summons, scale here. Skill damage
     * scales where each skill deals it.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide || !Config.EP_SCALING.get()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player && source.getDirectEntity() == player
                && source.is(DamageTypes.PLAYER_ATTACK) && CombatMode.isOn(player)) {
            event.setAmount(event.getAmount() * multiplier(player));
        } else if (attacker instanceof Player player && isTensuraAbility(source, player)) {
            event.setAmount(event.getAmount() * multiplier(player));
        } else if (attacker != null && source.getDirectEntity() == attacker
                && (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            Entity owner = summoner(attacker);
            if (owner != null) {
                event.setAmount(event.getAmount() * multiplier(owner));
            }
        }
    }

    /**
     * Whether a player's damage comes from one of Tensura's own skills or spells: one of Tensura's damage types (black
     * lightning, the elements, breaths, aura slashes...), or dealt through one of Tensura's projectiles, beams or magic
     * fields. Not ones this addon fired (already scaled), nor reflected damage.
     */
    static boolean isTensuraAbility(DamageSource source, Player player) {
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getPersistentData().getBoolean(SCALED_KEY)) {
            return false;
        }
        var type = source.typeHolder().unwrapKey();
        if (type.isPresent() && TENSURA.equals(type.get().location().getNamespace())) {
            String path = type.get().location().getPath();
            return !path.equals("reflected") && !path.equals("suicide");
        }
        return direct != null && direct != player
                && TENSURA.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace());
    }

    private static final String TENSURA = "tensura";

    /** Who summoned this creature with one of this addon's skills, if anyone (and they're around). */
    @Nullable
    static Entity summoner(Entity creature) {
        if (creature instanceof PaperBeastEntity beast) {
            return beast.getOwner();
        }
        if (creature instanceof ShikigamiEntity shikigami) {
            return shikigami.getOwner();
        }
        Binding binding = Binding.get(creature);
        if (binding != null) {
            return player(creature, binding.binder());
        }
        SoulBond bond = SoulBond.get(creature);
        if (bond != null) {
            return player(creature, bond.owner());
        }
        return null;
    }

    @Nullable
    private static Player player(Entity near, UUID id) {
        return near.level().getPlayerByUUID(id);
    }
}
