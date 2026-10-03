package com.tensurafragments.ally;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tensurafragments.TensuraFragments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Allies between players. Sneak and right-click another player with an empty hand to ask; when they do the same back
 * (or click Accept in chat, or use {@code /ally <you>}), you're allies, for good, until one of you uses
 * {@code /ally remove}. Allies can name each other's summons, and summons never attack an ally or an ally's summons.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID)
public final class Alliances {
    /** How long an ally request stays open. */
    private static final long REQUEST_TICKS = 2 * 60 * 20;
    /** Open requests: who asked whom, and when. */
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();

    private record Request(UUID to, long time) {
    }

    private Alliances() {
    }

    // ---- Who's allied ----

    public static boolean areAllies(MinecraftServer server, UUID a, UUID b) {
        return !a.equals(b) && data(server).pairs.contains(key(a, b));
    }

    /** A player's allies (online or not). */
    public static List<UUID> alliesOf(MinecraftServer server, UUID player) {
        List<UUID> allies = new ArrayList<>();
        for (String pair : data(server).pairs) {
            String[] ids = pair.split("\\|");
            if (ids[0].equals(player.toString())) {
                allies.add(UUID.fromString(ids[1]));
            } else if (ids[1].equals(player.toString())) {
                allies.add(UUID.fromString(ids[0]));
            }
        }
        return allies;
    }

    public static void ally(MinecraftServer server, UUID a, UUID b) {
        Data data = data(server);
        if (!a.equals(b) && data.pairs.add(key(a, b))) {
            data.setDirty();
        }
    }

    public static boolean unally(MinecraftServer server, UUID a, UUID b) {
        Data data = data(server);
        boolean removed = data.pairs.remove(key(a, b));
        if (removed) {
            data.setDirty();
        }
        return removed;
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    // ---- Asking ----

    /**
     * {@code from} asks {@code to} to be allies. If {@code to} already asked {@code from}, they're allies now.
     * Returns whether that made them allies.
     */
    public static boolean ask(ServerPlayer from, ServerPlayer to) {
        MinecraftServer server = from.server;
        if (from == to) {
            return false;
        }
        if (areAllies(server, from.getUUID(), to.getUUID())) {
            from.displayClientMessage(Component.translatable("tensurafragments.ally.already", to.getDisplayName()), true);
            return false;
        }
        long now = server.overworld().getGameTime();
        Request theirs = REQUESTS.get(to.getUUID());
        if (theirs != null && theirs.to().equals(from.getUUID()) && now - theirs.time() <= REQUEST_TICKS) {
            REQUESTS.remove(to.getUUID());
            REQUESTS.remove(from.getUUID());
            ally(server, from.getUUID(), to.getUUID());
            celebrate(from, to);
            celebrate(to, from);
            return true;
        }
        REQUESTS.put(from.getUUID(), new Request(to.getUUID(), now));
        from.sendSystemMessage(Component.translatable("tensurafragments.ally.asked", to.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
        MutableComponent accept = Component.translatable("tensurafragments.ally.accept").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ally " + from.getGameProfile().getName()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("tensurafragments.ally.accept_hover", from.getDisplayName()))));
        to.sendSystemMessage(Component.translatable("tensurafragments.ally.request", from.getDisplayName())
                .withStyle(ChatFormatting.YELLOW).append(" ").append(accept));
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.8F, 1.4F);
        return false;
    }

    private static void celebrate(ServerPlayer player, ServerPlayer ally) {
        player.sendSystemMessage(Component.translatable("tensurafragments.ally.formed", ally.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 2, player.getZ(),
                10, 0.4, 0.3, 0.4, 0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** Sneak and right-click another player with an empty hand. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getEntity().getMainHandItem().isEmpty() || !(event.getTarget() instanceof Player)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer from && event.getTarget() instanceof ServerPlayer to) {
            ask(from, to);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.getEntity().getUUID());
    }

    // ---- /ally ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ally")
                .then(Commands.literal("list").executes(Alliances::list))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        allyNames(context.getSource()), builder))
                                .executes(Alliances::remove)))
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    ask(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"));
                    return 1;
                })));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        context.getSource().getPlayerOrException();
        List<String> names = allyNames(context.getSource());
        context.getSource().sendSuccess(() -> names.isEmpty()
                ? Component.translatable("tensurafragments.ally.none")
                : Component.translatable("tensurafragments.ally.list", String.join(", ", names)), false);
        return names.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = player.server;
        for (UUID ally : alliesOf(server, player.getUUID())) {
            if (name.equalsIgnoreCase(nameOf(server, ally))) {
                unally(server, player.getUUID(), ally);
                context.getSource().sendSuccess(() -> Component.translatable("tensurafragments.ally.removed", name), false);
                ServerPlayer other = server.getPlayerList().getPlayer(ally);
                if (other != null) {
                    other.sendSystemMessage(Component.translatable("tensurafragments.ally.removed_by", player.getDisplayName()));
                }
                return 1;
            }
        }
        context.getSource().sendFailure(Component.translatable("tensurafragments.ally.not_ally", name));
        return 0;
    }

    private static List<String> allyNames(CommandSourceStack source) {
        List<String> names = new ArrayList<>();
        if (source.getEntity() instanceof ServerPlayer player) {
            for (UUID ally : alliesOf(source.getServer(), player.getUUID())) {
                names.add(nameOf(source.getServer(), ally));
            }
        }
        return names;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? id.toString()
                : server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString());
    }

    // ---- Saved with the world ----

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.FACTORY, "tensurafragments_allies");
    }

    private static final class Data extends SavedData {
        static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(Data::new, Data::load, null);
        final Set<String> pairs = new HashSet<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (Tag pair : tag.getList("Pairs", Tag.TAG_STRING)) {
                data.pairs.add(pair.getAsString());
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            pairs.forEach(pair -> list.add(StringTag.valueOf(pair)));
            tag.put("Pairs", list);
            return tag;
        }
    }

    /** Whether {@code entity} is an ally of {@code player}'s, or one of an ally's summons or tamed animals. */
    public static boolean isAlliedWith(@Nullable Entity entity, ServerPlayer player) {
        if (entity == null) {
            return false;
        }
        UUID owner = entity instanceof Player other ? other.getUUID()
                : entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null ? ownable.getOwnerUUID()
                : Companions.ownerOf(entity);
        return owner != null && areAllies(player.server, owner, player.getUUID());
    }
}
