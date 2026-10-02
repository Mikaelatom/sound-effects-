package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.yifa.SpiritCommunion;
import com.tensurafragments.yifa.SpiritElement;
import com.tensurafragments.yifa.WispEntity;
import io.github.manasmods.tensura.storage.TensuraStorages;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only visual check, inert unless the game is started with {@code -Dtensurafragments.visualCheck=true}
 * ({@code ./gradlew runClient -PvisualCheck}). It builds a small scene in a singleplayer world, casts Draconic Hell
 * Storm and a Rainbow Magic spell, takes screenshots, and quits.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class VisualCheck {
    private static final boolean ENABLED = Boolean.getBoolean("tensurafragments.visualCheck");
    private static int ticks;

    private VisualCheck() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            return;
        }
        if (mc.screen != null) {
            // Tensura's reincarnation (race) screen opens on first join; close it so the check can run.
            org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLIENT closing screen {}", mc.screen.getClass().getName());
            mc.setScreen(null);
            return;
        }
        ticks++;
        switch (ticks) {
            case 40 -> onServer(mc, VisualCheck::buildScene);
            case 60 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 95 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 99 -> {
                mc.player.setYRot(90);
                mc.player.setXRot(-25);
            }
            case 100 -> onServer(mc, player -> {
                player.setExperienceLevels(50);
                player.setData(com.tensurafragments.ModRegistries.ENERGY_SPELL,
                        com.tensurafragments.energy.EnergySpell.FIRE_BALL.ordinal());
                // Up into open sky, so the spell is still flying when the screenshot is taken.
                player.setYRot(90);
                player.setXRot(-25);
                com.tensurafragments.energy.EnergyMagic.cast(player);
            });
            case 105 -> shot(mc, "1_energy_fire_ball");
            case 106 -> onServer(mc, player -> {
                for (net.minecraft.world.entity.monster.Husk husk : player.serverLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.monster.Husk.class, player.getBoundingBox().inflate(30))) {
                    com.tensurafragments.energy.EnergyMagic.mark(husk);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new com.tensurafragments.network.EnergyEntityPayload(husk.getId()));
                }
            });
            case 112 -> shot(mc, "1b_energy_husks");
            case 120 -> onServer(mc, player -> {
                player.setData(com.tensurafragments.ModRegistries.ENERGY_SPELL,
                        com.tensurafragments.energy.EnergySpell.ICE_LANCE.ordinal());
                com.tensurafragments.energy.EnergyMagic.cast(player);
            });
            case 122 -> shot(mc, "2_energy_ice_lance");
            case 140 -> shot(mc, "3_energy_after");
            case 145 -> {
                mc.player.setYRot(0);
                mc.player.setXRot(10);
            }
            case 150 -> onServer(mc, player -> {
                player.setYRot(0);
                player.setXRot(10);
                player.setData(com.tensurafragments.ModRegistries.SOULS, new java.util.ArrayList<>(java.util.List.of(
                        new com.tensurafragments.soul.CapturedSoul("minecraft:husk", "Husk", 40),
                        new com.tensurafragments.soul.CapturedSoul(com.tensurafragments.soul.CapturedSoul.PLAYER, "Steve", 100),
                        new com.tensurafragments.soul.CapturedSoul("minecraft:zombie", "Zombie", 1.0E6))));
                player.setData(com.tensurafragments.ModRegistries.SELECTED_SOUL, 0);
                TensuraStorages.getAbilityFrom(player).setAbilitySlot(1, ModSkills.SOUL_REAPER.get(), 0);
                io.github.manasmods.tensura.storage.TensuraStorages.getExistenceFrom(player).setSoulPoints(20_000);
                com.tensurafragments.soul.SoulReaper.summon(player);
                player.setData(com.tensurafragments.ModRegistries.SELECTED_SOUL, 1);
                player.setYRot(-35);
                com.tensurafragments.soul.SoulReaper.summon(player);
                // Possess the nearest husk with the strong soul.
                Husk nearest = player.serverLevel().getNearestEntity(Husk.class,
                        net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat(), player,
                        player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(30));
                if (nearest != null && com.tensurafragments.soul.SoulBond.get(nearest) == null) {
                    player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            nearest.position().add(0, 1, 0));
                    player.setData(com.tensurafragments.ModRegistries.SELECTED_SOUL, 2);
                    com.tensurafragments.soul.SoulReaper.possess(player);
                }
                player.setYRot(0);
                player.setXRot(10);
            });
            case 160 -> shot(mc, "4_souls");
            case 162 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 168 -> shot(mc, "5_souls_behind");
            case 175 -> onServer(mc, player -> {
                player.setGameMode(GameType.SURVIVAL);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 2000, 4));
                io.github.manasmods.manascore.race.api.RaceAPI.getRaceFrom(player)
                        .setRace(com.tensurafragments.spiritrace.SpiritRaces.LESSER.get(), true);
                TensuraStorages.getExistenceFrom(player).setSpiritualForm(true);
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("SPIRIT levels {} now in {}",
                        player.server.levelKeys(), player.level().dimension());
            });
            case 190 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setXRot(30);
            }
            case 216 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 215 -> shot(mc, "6_spirit_realm");
            case 220 -> onServer(mc, player -> {
                com.tensurafragments.spiritrace.SpiritPassage.use(player);
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("SPIRIT crossed to {} deadline {}",
                        player.level().dimension(), com.tensurafragments.spiritrace.SpiritPassage.deadline(player));
            });
            case 260 -> shot(mc, "7_material_world");
            case 265 -> onServer(mc, player -> {
                // As if it had possessed a body: release everything.
                TensuraStorages.getExistenceFrom(player).setSpiritualForm(false);
                com.tensurafragments.spiritrace.SpiritRelease.start(player, 100);
            });
            case 270 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(15);
            }
            case 285 -> shot(mc, "8_spirit_release");
            case 300 -> mc.stop();
            default -> {
            }
        }
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        });
    }

    private static void buildScene(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        player.setGameMode(GameType.CREATIVE);
        BlockPos base = new BlockPos(player.getBlockX(), 200, player.getBlockZ());
        for (int x = -6; x <= 6; x++) {
            for (int z = -4; z <= 24; z++) {
                level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        player.teleportTo(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0F, 8F);
        for (int i = 0; i < 3; i++) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk != null) {
                husk.moveTo(base.getX() + 0.5 + (i - 1) * 2, base.getY() + 1, base.getZ() + 8.5 + i * 4, 180F, 0F);
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
            }
        }
        var ability = TensuraStorages.getAbilityFrom(player);
        if (ability != null) {
            ability.setAbilitySlot(0, ModSkills.ENERGY_MAGIC.get(), 0);
            ability.setAbilitySlot(1, ModSkills.SOUL_REAPER.get(), 0);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "visualcheck_" + name + ".png", mc.getMainRenderTarget(), message -> { });
    }
}
