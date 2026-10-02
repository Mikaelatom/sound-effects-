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
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container
                && container.getMenu() instanceof io.github.manasmods.tensura.menu.ReincarnationMenu menu) {
            // What the race selection screen offers, on the client.
            org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLIENT race menu offers {}",
                    menu.getRacePool().stream().map(r -> r.getRegistryName().toString()).toList());
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
            mc.player.respawn();
            mc.setScreen(null);
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
            case 170 -> onServer(mc, player -> dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(player,
                    new net.minecraft.world.SimpleMenuProvider((id, inventory, p) ->
                            new io.github.manasmods.tensura.menu.ReincarnationMenu(id, inventory, p),
                            net.minecraft.network.chat.Component.translatable("tensura.reincarnation")),
                    buf -> {
                        buf.writeBoolean(false);
                        buf.writeInt(0);
                        buf.writeFloat(0);
                        buf.writeFloat(0);
                    }));
            case 175 -> onServer(mc, player -> {
                player.setGameMode(GameType.SURVIVAL);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 6000, 4));
                // The race selection screen's own path.
                io.github.manasmods.tensura.menu.ReincarnationMenu.setRace(player,
                        com.tensurafragments.spiritrace.SpiritRaces.LESSER.get(), true, false);
                spiritLog("chose Lesser Spirit", player);
            });
            case 200 -> onServer(mc, player -> {
                io.github.manasmods.manascore.race.api.RaceAPI.getRaceFrom(player).getRace().orElseThrow()
                        .onActivateAbility(player);
                spiritLog("pressed R (flight " + player.getAbilities().mayfly + ")", player);
            });
            case 190 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setXRot(30);
            }
            case 214 -> shot(mc, "6_spirit_realm");
            case 216 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 220 -> onServer(mc, player -> {
                com.tensurafragments.spiritrace.SpiritPassage.use(player);
                spiritLog("read the book", player);
            });
            case 255 -> onServer(mc, player -> spiritLog("40 ticks later", player));
            case 256 -> shot(mc, "7_material_world");
            case 258 -> onServer(mc, player -> {
                // A weakened husk right in front, then Tensura's own Possession skill.
                Husk husk = EntityType.HUSK.create(player.serverLevel());
                net.minecraft.world.phys.Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
                husk.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
                husk.setNoAi(true);
                player.serverLevel().addFreshEntity(husk);
                husk.setHealth(1);
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, husk.position().add(0, 1, 0));
                var possession = io.github.manasmods.manascore.skill.api.SkillAPI.getSkillsFrom(player)
                        .getSkill(io.github.manasmods.tensura.registry.skill.IntrinsicSkills.POSSESSION.get()).orElseThrow();
                possession.getSkill().onPressed(possession, player, 0, 0);
                spiritLog("pressed Possession", player);
            });
            case 262 -> onServer(mc, player -> spiritLog("after possessing", player));
            case 263 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(10);
            }
            case 268 -> shot(mc, "8_possessed");
            case 270 -> onServer(mc, player -> {
                com.tensurafragments.spiritrace.SpiritRelease.start(player, 10);
                spiritLog("released 10%", player);
            });
            case 272 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(10);
            }
            case 281 -> shot(mc, "9a_release_10");
            case 282 -> onServer(mc, player -> {
                com.tensurafragments.spiritrace.SpiritRelease.end(player, true);
                com.tensurafragments.spiritrace.SpiritRelease.start(player, 50);
            });
            case 293 -> shot(mc, "9b_release_50");
            case 296 -> onServer(mc, player -> {
                com.tensurafragments.spiritrace.SpiritRelease.end(player, true);
                com.tensurafragments.spiritrace.SpiritRelease.start(player, 100);
            });
            case 307 -> shot(mc, "9c_release_100");
            case 309 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 312 -> onServer(mc, player -> {
                player.removeAllEffects();
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            });
            case 330 -> onServer(mc, player -> spiritLog("respawned", player));
            case 335 -> shot(mc, "10_respawned");
            case 338 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setYRot(0);
                mc.player.setXRot(20);
            }
            case 340 -> onServer(mc, player -> {
                player.setGameMode(GameType.CREATIVE);
                player.setYRot(0);
                // Three husks in a row ahead: Gale, Flame + Gale, Thunder + Gale.
                com.tensurafragments.card.SpellCard[][] hands = {
                        {com.tensurafragments.card.SpellCard.GALE},
                        {com.tensurafragments.card.SpellCard.FLAME, com.tensurafragments.card.SpellCard.GALE},
                        {com.tensurafragments.card.SpellCard.THUNDER, com.tensurafragments.card.SpellCard.GALE}};
                for (int i = 0; i < hands.length; i++) {
                    Husk husk = EntityType.HUSK.create(player.serverLevel());
                    husk.moveTo(player.getX() + (i - 1) * 9, player.getY(), player.getZ() + 14, 180, 0);
                    husk.setNoAi(true);
                    player.serverLevel().addFreshEntity(husk);
                    com.tensurafragments.card.CardEntity first = null;
                    for (var spell : hands[i]) {
                        var card = com.tensurafragments.card.CardEntity.createSpell(player, spell);
                        player.serverLevel().addFreshEntity(card);
                        card.attachTo(husk);
                        first = first == null ? card : first;
                    }
                    first.detonate();
                }
            });
            case 356 -> shot(mc, "11_tornadoes");
            case 370 -> onServer(mc, player -> {
                Husk husk = EntityType.HUSK.create(player.serverLevel());
                husk.moveTo(player.getX(), player.getY(), player.getZ() + 12, 180, 0);
                husk.setNoAi(true);
                player.serverLevel().addFreshEntity(husk);
                var quake = com.tensurafragments.card.CardEntity.createSpell(player, com.tensurafragments.card.SpellCard.QUAKE);
                var meteor = com.tensurafragments.card.CardEntity.createSpell(player, com.tensurafragments.card.SpellCard.METEOR);
                player.serverLevel().addFreshEntity(quake);
                player.serverLevel().addFreshEntity(meteor);
                quake.attachTo(husk);
                meteor.attachTo(husk);
                quake.detonate();
            });
            case 397 -> shot(mc, "12_meteor");
            case 413 -> shot(mc, "13_cataclysm");
            case 430 -> mc.stop();
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

    private static void spiritLog(String what, ServerPlayer player) {
        org.slf4j.LoggerFactory.getLogger("visualcheck").warn("SPIRIT {}: race {} in {} spiritual {} clock {} magicules {}",
                what, io.github.manasmods.manascore.race.api.RaceAPI.getRaceFrom(player).getRace()
                        .map(r -> r.getRaceId().toString()).orElse("none"),
                player.level().dimension().location(), com.tensurafragments.spiritrace.SpiritPassage.isSpiritual(player),
                com.tensurafragments.spiritrace.SpiritPassage.deadline(player) == 0 ? "off"
                        : (com.tensurafragments.spiritrace.SpiritPassage.deadline(player) - player.level().getGameTime()) / 20 + "s",
                Math.round(TensuraStorages.getExistenceFrom(player).getMagicule()));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "visualcheck_" + name + ".png", mc.getMainRenderTarget(), message -> { });
    }
}
