package com.tensurafragments.test;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.skill.ModSkills;
import com.tensurafragments.yifa.FireWhirlEntity;
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
        if (!ENABLED || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.screen != null) {
            return;
        }
        ticks++;
        if (ticks >= 100 && ticks <= 170 && ticks % 5 == 0) {
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof FireWhirlEntity whirl) {
                    org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLIENT whirl tickCount={} pos={} old={}",
                            whirl.tickCount, whirl.position(), new net.minecraft.world.phys.Vec3(whirl.xOld, whirl.yOld, whirl.zOld));
                }
            }
        }
        switch (ticks) {
            case 40 -> onServer(mc, VisualCheck::buildScene);
            case 60 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 95 -> onServer(mc, player -> {
                SpiritCommunion.setSight(player, true);
                for (SpiritElement element : new SpiritElement[] {SpiritElement.FIRE, SpiritElement.WIND, SpiritElement.WIND}) {
                    player.level().addFreshEntity(WispEntity.wild(player.level(), element, player.position().add(0, 1, 1)));
                }
            });
            case 98 -> onServer(mc, player -> {
                player.setShiftKeyDown(true);
                SpiritCommunion.call(player);
                player.setShiftKeyDown(false);
                player.setYRot(0);
                player.setXRot(8);
                SpiritCommunion.release(player);
            });
            case 110 -> shot(mc, "1_whirl_start");
            case 125 -> shot(mc, "2_whirl_mid");
            case 140 -> shot(mc, "3_whirl_late");
            case 180 -> mc.stop();
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
            ability.setAbilitySlot(0, ModSkills.SPIRIT_COMMUNION.get(), 2);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "visualcheck_" + name + ".png", mc.getMainRenderTarget(), message -> { });
    }
}
