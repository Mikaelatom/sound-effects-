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
        boolean ours = mc.screen instanceof com.tensurafragments.client.RuneScreens.Canvas
                || mc.screen instanceof com.tensurafragments.client.RuneScreens.Codex
                || mc.screen instanceof com.tensurafragments.client.SkillPickScreen;
        if (mc.screen != null && !ours) {
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
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("EP before death: {}",
                        io.github.manasmods.tensura.util.EnergyHelper.getBaseMaxEP(player));
                player.removeAllEffects();
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            });
            case 330 -> onServer(mc, player -> {
                spiritLog("respawned", player);
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("EP after death: {}",
                        io.github.manasmods.tensura.util.EnergyHelper.getBaseMaxEP(player));
            });
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
            case 416 -> onServer(mc, player -> {
                io.github.manasmods.tensura.ability.SkillHelper.learnSkill(player,
                        com.tensurafragments.skill.ModSkills.SHIKIGAMI_CONTROL.get());
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 16));
                TensuraStorages.getExistenceFrom(player).setMagicule(100_000);
                player.setData(com.tensurafragments.ModRegistries.SELECTED_BEAST,
                        com.tensurafragments.shikigami.BeastKind.OWL.ordinal());
                com.tensurafragments.shikigami.PaperBeasts.fold(player);
                var beasts = com.tensurafragments.shikigami.PaperBeasts.beasts(player);
                com.tensurafragments.shikigami.PaperBeasts.possess(player, beasts.get(beasts.size() - 1));
            });
            case 640 -> {
                net.minecraft.world.entity.Entity camera = mc.getCameraEntity();
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLIENT FAR BEAST: camera {} {} blocks from body at {}, chunk loaded {}",
                        camera.getType(), Math.round(camera.distanceTo(mc.player)), mc.player.position(),
                        mc.level.getChunkSource().hasChunk(camera.chunkPosition().x, camera.chunkPosition().z));
                onServer(mc, player -> {
                    var beast = com.tensurafragments.shikigami.PaperBeasts.possessed(player);
                    org.slf4j.LoggerFactory.getLogger("visualcheck").warn("SERVER FAR BEAST: possessing {} at {} blocks",
                            beast, beast == null ? -1 : Math.round(beast.distanceTo(player)));
                });
            }
            case 641 -> shot(mc, "14_beast_far");
            case 645 -> onServer(mc, com.tensurafragments.shikigami.PaperBeasts::release);
            case 665 -> {
                shot(mc, "15_back_in_body");
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLIENT BACK: body at {}, health {}", mc.player.position(),
                        mc.player.getHealth());
            }
            case 668 -> onServer(mc, player -> {
                // Creative: Tensura's Naming is free, so this doesn't depend on the scene's magicules.
                player.setGameMode(GameType.CREATIVE);
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 40));
                player.setData(com.tensurafragments.ModRegistries.SELECTED_BEAST,
                        com.tensurafragments.shikigami.BeastKind.HOUND.ordinal());
                player.setYRot(0);
                player.setXRot(30);
                for (int i = 0; i < 3; i++) {
                    org.slf4j.LoggerFactory.getLogger("visualcheck").warn("FOLD {}", com.tensurafragments.shikigami.PaperBeasts.fold(player));
                }
                net.minecraft.world.item.ItemStack named = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NAME_TAG);
                named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Patch"));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, named);
                player.getInventory().setItem(5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NAME_TAG));
            });
            // An anvil-named tag.
            case 676 -> clickNearest(mc, com.tensurafragments.shikigami.PaperBeastEntity.class);
            // A blank tag, then the name typed in chat.
            case 680 -> onServer(mc, player -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    player.getInventory().removeItem(5, 1)));
            case 686 -> clickNearest(mc, com.tensurafragments.shikigami.PaperBeastEntity.class);
            case 690 -> mc.player.connection.sendChat("Biscuit");
            // Tensura's own Naming, through its key packet and naming screen.
            case 693 -> {
                net.minecraft.world.entity.Entity hound = nearestUnnamed(mc, com.tensurafragments.shikigami.PaperBeastEntity.class);
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, hound.position().add(0, 0.5, 0));
                onServer(mc, player -> player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        hound.position().add(0, 0.5, 0)));
            }
            case 695 -> dev.architectury.networking.NetworkManager.sendToServer(
                    new io.github.manasmods.tensura.network.c2s.RequestNamingKeyPacket());
            case 697 -> {
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("TENSURA NAMING screen: {}", mc.screen);
                net.minecraft.world.entity.Entity hound = nearestUnnamed(mc, com.tensurafragments.shikigami.PaperBeastEntity.class);
                dev.architectury.networking.NetworkManager.sendToServer(new io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket(
                        hound.getId(), "Rex", io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket.NamingType.LOW));
            }
            case 699 -> {
                if (mc.screen != null) {
                    mc.player.closeContainer();
                }
            }
            case 700 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, player -> com.tensurafragments.ally.Companions.companions(player).forEach(e ->
                        org.slf4j.LoggerFactory.getLogger("visualcheck").warn("NAMED: {} called {}", e.getType(),
                                e.getCustomName() == null ? null : e.getCustomName().getString())));
            }
            case 712 -> shot(mc, "16_named");
            case 715 -> onServer(mc, player -> {
                io.github.manasmods.tensura.ability.SkillHelper.learnSkill(player, com.tensurafragments.skill.ModSkills.RUNE_MAGIC.get());
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 5));
            });
            case 718 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                com.tensurafragments.client.SkillPickScreen.open(com.tensurafragments.skill.StartingSkill.CHOICES.stream()
                        .map(choice -> choice.getId().toString()).toList());
            }
            case 722 -> shot(mc, "17_skill_pick");
            case 724 -> mc.setScreen(null);
            case 726 -> onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn("RUNE startDrawing {}",
                    com.tensurafragments.rune.RuneMagic.startDrawing(player)));
            case 730 -> {
                // Draw Thunder with real mouse events, dot by dot.
                var screen = mc.screen;
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("RUNE canvas open: {}", screen);
                if (screen != null) {
                    int[][] path = {{3, 0}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {2, 3}, {1, 4}};
                    double[] first = runeDot(screen, path[0]);
                    screen.mouseClicked(first[0], first[1], 0);
                    for (int[] dot : path) {
                        double[] at = runeDot(screen, dot);
                        screen.mouseDragged(at[0], at[1], 0, 0, 0);
                    }
                    double[] last = runeDot(screen, path[path.length - 1]);
                    screen.mouseReleased(last[0], last[1], 0);
                }
            }
            case 740 -> shot(mc, "18_rune_canvas");
            case 744 -> {
                var screen = mc.screen;
                if (screen != null) {
                    // The Inscribe button.
                    screen.mouseClicked(screen.width / 2 + 110, screen.height / 2 - 86 + 160 + 22 + 10, 0);
                }
            }
            case 748 -> onServer(mc, player -> {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    var rune = com.tensurafragments.rune.RunePaperItem.runeOf(player.getInventory().getItem(i));
                    if (rune != null) {
                        org.slf4j.LoggerFactory.getLogger("visualcheck").warn("RUNE drawn onto paper: {}", rune);
                    }
                }
            });
            case 750 -> com.tensurafragments.client.RuneScreens.openCodex();
            case 756 -> shot(mc, "19_rune_codex");
            case 758 -> {
                mc.setScreen(null);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, player -> {
                    // A clean stage high up.
                    ServerLevel level = player.serverLevel();
                    BlockPos base = new BlockPos(player.getBlockX() + 40, 230, player.getBlockZ());
                    for (int x = -6; x <= 6; x++) {
                        for (int z = -4; z <= 10; z++) {
                            level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        }
                    }
                    player.teleportTo(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0F, 10F);
                    // Husks from earlier runs of this persistent world.
                    level.getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(30)).forEach(Husk::discard);
                    // Earlier scenes can leave Magicule Poison behind; clear it so nothing dies mid-scene.
                    player.removeAllEffects();
                    TensuraStorages.getExistenceFrom(player).setMagicule(0);
                    player.setHealth(player.getMaxHealth());
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.getInventory().setItem(player.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
                    player.setData(com.tensurafragments.ModRegistries.COMBAT_MODE, true);
                    com.tensurafragments.combat.CombatMode.sync(player, 0);
                    player.setYRot(0);
                    player.setXRot(10);
                    for (int i = 0; i < 3; i++) {
                        Husk husk = EntityType.HUSK.create(player.serverLevel());
                        husk.moveTo(player.getX() + (i - 1) * 1.2, player.getY(), player.getZ() + 2.2, 180, 0);
                        // Not NoAI (that also stops them being knocked about): just too slow to go anywhere.
                        husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 10, false, false));
                        husk.setPersistenceRequired();
                        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
                        husk.setHealth(500);
                        player.serverLevel().addFreshEntity(husk);
                    }
                });
            }
            case 762, 768, 774 -> {
                // Real punches: the attack key, at the husk in the crosshair.
                net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
            }
            case 763, 769, 775 -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn(
                    "COMBAT punch with {} (target {}, main hand {}, combat {})", mc.player.swingingArm,
                    mc.hitResult == null ? null : mc.hitResult.getType(), mc.player.getMainHandItem(),
                    com.tensurafragments.client.ClientCombat.isOn());
            case 778 -> mc.player.jumpFromGround();
            case 780 -> {
                // Look down at the nearest husk's chest, from midair.
                mc.level.getEntitiesOfClass(Husk.class, mc.player.getBoundingBox().inflate(5)).stream()
                        .min(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(mc.player)))
                        .ifPresent(h -> mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                                h.position().add(0, 1.0, 0)));
                mc.gameRenderer.pick(1.0F);
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT uppercut try: onGround {} dy {} aiming at {}",
                        mc.player.onGround(), mc.player.getDeltaMovement().y,
                        mc.hitResult == null ? null : mc.hitResult.getType());
                net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
            }
            case 781 -> onServer(mc, player -> player.serverLevel().getEntitiesOfClass(Husk.class,
                    player.getBoundingBox().inflate(6)).forEach(h -> org.slf4j.LoggerFactory.getLogger("visualcheck")
                    .warn("COMBAT husk after uppercut: y-speed {} height {}", h.getDeltaMovement().y,
                            h.getY() - player.getY())));
            case 770 -> shot(mc, "20_combo");
            case 784 -> shot(mc, "21_uppercut");
            case 786 -> {
                mc.options.keyShift.setDown(true);
                onServer(mc, player -> player.teleportTo(player.getX(), player.getY() + 12, player.getZ() + 1.5));
            }
            case 789 -> {
                net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT slam pressed: onGround {} shift {}",
                        mc.player.onGround(), mc.player.isShiftKeyDown());
            }
            case 791 -> onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn(
                    "COMBAT slamming {} y {}", com.tensurafragments.combat.CombatMode.isSlamming(player), player.getY()));
            case 799 -> shot(mc, "22a_slam_landing");
            case 801 -> shot(mc, "22b_slam_landing");
            case 771 -> shot(mc, "20b_combo");
            case 797 -> {
                mc.options.keyShift.setDown(false);
                shot(mc, "22_slam");
            }
            case 805 -> onServer(mc, player -> player.serverLevel().getEntitiesOfClass(Husk.class,
                    player.getBoundingBox().inflate(8)).forEach(h -> org.slf4j.LoggerFactory.getLogger("visualcheck")
                    .warn("COMBAT husk hp {}", h.getHealth())));
            case 810 -> onServer(mc, player -> {
                // Grab, throw, dash and block: one fresh husk right in front.
                player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(40), e -> !(e instanceof net.minecraft.world.entity.player.Player))
                        .forEach(e -> e.discard());
                player.setYRot(0);
                player.setXRot(15);
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 15);
                Husk husk = EntityType.HUSK.create(player.serverLevel());
                husk.moveTo(player.getX(), player.getY(), player.getZ() + 2.0, 180, 0);
                husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 10, false, false));
                husk.setPersistenceRequired();
                player.serverLevel().addFreshEntity(husk);
            });
            case 814 -> net.minecraft.client.KeyMapping.click(com.tensurafragments.client.ClientCombat.GRAB.getKey());
            case 817 -> {
                onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT grabbing {}",
                        com.tensurafragments.combat.CombatMode.isGrabbing(player)));
                shot(mc, "23_grab");
            }
            case 818 -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            case 819 -> shot(mc, "23b_grab_first_person");
            case 820 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            case 822 -> net.minecraft.client.KeyMapping.click(com.tensurafragments.client.ClientCombat.GRAB.getKey());
            case 823 -> onServer(mc, player -> player.serverLevel().getEntitiesOfClass(Husk.class,
                    player.getBoundingBox().inflate(8)).forEach(h -> org.slf4j.LoggerFactory.getLogger("visualcheck")
                    .warn("COMBAT thrown husk: motion {} hp {}", h.getDeltaMovement(), h.getHealth())));
            case 825 -> shot(mc, "24_throw");
            case 832 -> {
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT before dash at {}", mc.player.position());
                net.minecraft.client.KeyMapping.click(com.tensurafragments.client.ClientCombat.DASH.getKey());
            }
            case 834 -> {
                onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT dodging {}",
                        com.tensurafragments.combat.CombatMode.isDodging(player)));
                shot(mc, "25_dash");
            }
            case 840 -> {
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT after dash at {}", mc.player.position());
                net.minecraft.client.KeyMapping.set(com.tensurafragments.client.ClientCombat.BLOCK.getKey(), true);
            }
            case 843 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            case 845 -> {
                onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT blocking {} (client {})",
                        com.tensurafragments.combat.CombatMode.isBlocking(player),
                        com.tensurafragments.client.ClientCombat.isBlocking()));
                shot(mc, "26_block");
            }
            case 846 -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            case 847 -> shot(mc, "26b_block_first_person");
            case 848 -> {
                net.minecraft.client.KeyMapping.set(com.tensurafragments.client.ClientCombat.BLOCK.getKey(), false);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            }
            case 852 -> onServer(mc, player -> {
                player.serverLevel().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(30)).forEach(Husk::discard);
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 15);
                for (int i = 0; i < 2; i++) {
                    Husk husk = EntityType.HUSK.create(player.serverLevel());
                    husk.moveTo(player.getX() + i * 1.5 - 0.75, player.getY(), player.getZ() + 2.0 + i, 180, 0);
                    husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 10, false, false));
                    husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
                    husk.setHealth(500);
                    husk.setPersistenceRequired();
                    player.serverLevel().addFreshEntity(husk);
                }
            });
            case 853, 855, 857 -> net.minecraft.client.KeyMapping.click(com.tensurafragments.client.ClientCombat.STYLE.getKey());
            case 860, 864, 868 -> net.minecraft.client.KeyMapping.click(
                    com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
            case 870 -> {
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT style {}",
                        com.tensurafragments.client.ClientCombat.style());
                shot(mc, "27_ki_down_bar");
            }
            case 872 -> net.minecraft.client.KeyMapping.click(com.tensurafragments.client.ClientCombat.GRAB.getKey());
            case 874 -> {
                onServer(mc, player -> player.serverLevel().getEntitiesOfClass(Husk.class,
                        player.getBoundingBox().inflate(10)).forEach(h -> org.slf4j.LoggerFactory.getLogger("visualcheck")
                        .warn("COMBAT after ki burst: downed {} motion {}", com.tensurafragments.combat.CombatMode.isDowned(h),
                                h.getDeltaMovement())));
                shot(mc, "28_ki_burst");
            }
            case 878 -> onServer(mc, player -> {
                player.serverLevel().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(30)).forEach(Husk::discard);
                com.tensurafragments.combat.CombatMode.setStyle(player, com.tensurafragments.combat.FightingStyle.SWIFT);
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 20);
                Husk husk = EntityType.HUSK.create(player.serverLevel());
                husk.moveTo(player.getX() + 0.8, player.getY(), player.getZ() + 3.2, 180, 0);
                husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 10, false, false));
                husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
                husk.setHealth(500);
                husk.setPersistenceRequired();
                player.serverLevel().addFreshEntity(husk);
            });
            case 884 -> mc.player.jumpFromGround();
            case 885 -> mc.options.keyShift.setDown(true);
            case 886 -> {
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("COMBAT dive try: onGround {} style {} shift {} pos {}",
                        mc.player.onGround(), com.tensurafragments.client.ClientCombat.style(), mc.player.isShiftKeyDown(),
                        mc.player.position());
                net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
            }
            case 887, 889, 891, 892 -> onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn(
                    "COMBAT dive t{}: diving {} pos {} ground {} motion {} husks {}", ticks,
                    com.tensurafragments.combat.StyleMoves.isDiving(player), player.position(), player.onGround(),
                    player.getDeltaMovement(), player.serverLevel().getEntitiesOfClass(Husk.class,
                            player.getBoundingBox().inflate(10)).stream().map(h -> h.position().toString()).toList()));
            case 888 -> {
                shot(mc, "29_dive_kick");
                onServer(mc, player -> org.slf4j.LoggerFactory.getLogger("visualcheck").warn(
                        "COMBAT dive t888: diving {} pos {} ground {}", com.tensurafragments.combat.StyleMoves.isDiving(player),
                        player.position(), player.onGround()));
            }
            case 890 -> mc.options.keyShift.setDown(false);
            case 894 -> onServer(mc, player -> player.serverLevel().getEntitiesOfClass(Husk.class,
                    player.getBoundingBox().inflate(10)).forEach(h -> org.slf4j.LoggerFactory.getLogger("visualcheck")
                    .warn("COMBAT after dive kick: husk hp {}", h.getHealth())));
            case 898 -> onServer(mc, player -> {
                player.serverLevel().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(30)).forEach(Husk::discard);
                com.tensurafragments.combat.CombatMode.setStyle(player, com.tensurafragments.combat.FightingStyle.BRAWLER);
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 0);
            });
            case 900 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            case 906 -> shot(mc, "30_guard");
            case 910, 922 -> net.minecraft.client.KeyMapping.click(
                    com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
            case 913 -> shot(mc, "31_hook_a");
            case 925 -> shot(mc, "32_hook_b");
            case 935 -> shot(mc, "33_guard_again");
            case 940 -> mc.player.jumpFromGround();
            case 941 -> net.minecraft.client.KeyMapping.click(
                    com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0));
            case 944 -> shot(mc, "34_uppercut_a");
            case 947 -> shot(mc, "35_uppercut_b");
            case 956 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            case 958 -> mc.stop();
            default -> {
                if (ticks > 418 && ticks < 638) {
                    // Fly the possessed owl 330 blocks east, a bit faster than it flies on its own.
                    onServer(mc, player -> {
                        var beast = com.tensurafragments.shikigami.PaperBeasts.possessed(player);
                        if (beast != null) {
                            double before = beast.getX();
                            beast.teleportTo(beast.getX() + 1.5, 215, beast.getZ());
                        }
                        if (ticks % 40 == 0) {
                            org.slf4j.LoggerFactory.getLogger("visualcheck").warn("FAR BEAST t{}: body {} beast {}", ticks,
                                    player.position(), beast == null ? null : beast.position());
                        }
                    });
                }
            }
        }
    }

    private static net.minecraft.world.entity.Entity nearestUnnamed(Minecraft mc, Class<? extends net.minecraft.world.entity.Entity> type) {
        net.minecraft.world.entity.Entity nearest = null;
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (type.isInstance(entity) && entity.getCustomName() == null
                    && (nearest == null || entity.distanceTo(mc.player) < nearest.distanceTo(mc.player))) {
                nearest = entity;
            }
        }
        return nearest;
    }

    /** A real right-click (through the client, packets and all) on the nearest unnamed entity of a type. */
    private static void clickNearest(Minecraft mc, Class<? extends net.minecraft.world.entity.Entity> type) {
        net.minecraft.world.entity.Entity nearest = nearestUnnamed(mc, type);
        org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLICK {} at {} blocks holding {}: {}", nearest,
                nearest == null ? -1 : nearest.distanceTo(mc.player), mc.player.getMainHandItem(),
                nearest == null ? null : mc.gameMode.interact(mc.player, nearest, net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    /** Where a rune dot is on the drawing sheet (the sheet is centred: dots 40 apart, 6 above the middle). */
    private static double[] runeDot(net.minecraft.client.gui.screens.Screen screen, int[] dot) {
        return new double[] {screen.width / 2 - 80 + dot[0] * 40, screen.height / 2 - 86 + dot[1] * 40};
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
