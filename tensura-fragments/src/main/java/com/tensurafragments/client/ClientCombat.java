package com.tensurafragments.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.CombatInputPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Combat Mode on the client: its key (G), the down slam input, and the combo counter by the crosshair. */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class ClientCombat {
    public static final KeyMapping TOGGLE = new KeyMapping("key.tensurafragments.combat_mode", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.tensurafragments");
    private static boolean on;
    private static int combo;
    private static long comboShownAt;
    /** Jabs thrown in a row (for alternating hands), and when the last was. */
    private static int jabs;
    private static long lastJab;

    private ClientCombat() {
    }

    public static boolean isOn() {
        return on;
    }

    public static void sync(boolean isOn, int count) {
        on = isOn;
        combo = count;
        comboShownAt = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.TOGGLE));
            }
        }
    }

    /**
     * In Combat Mode: attacking while sneaking in mid-air is a down slam; attacking while rising from a jump is an
     * uppercut; and jabs (unless you hold a weapon or tool) alternate right and left hands.
     */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || !event.isAttack() || mc.player == null || mc.level == null || ClientPossession.isPossessing()) {
            return;
        }
        if (!mc.player.onGround() && mc.player.isShiftKeyDown() && !mc.player.isInWater()) {
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.SLAM));
            event.setSwingHand(true);
            event.setCanceled(true);
            return;
        }
        if (!mc.player.onGround() && mc.player.getDeltaMovement().y > 0 && !mc.player.isInWater()
                && !mc.player.getAbilities().flying) {
            // Sent before the attack itself, so the server knows this punch is the uppercut.
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.UPPERCUT));
            jabs = 0;
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastJab > 30) {
            jabs = 0;
        }
        lastJab = now;
        jabs++;
        // Unless it's a weapon or tool in your hand, punches go right, left, right...
        if (jabs % 2 == 0 && !mc.player.getMainHandItem().isDamageableItem()) {
            // The left hand's turn: swing it instead (the swing is sent on to the server and everyone else).
            event.setSwingHand(false);
            mc.player.swing(InteractionHand.OFF_HAND);
        }
    }

    /** "Combat" under the crosshair while it's on, and the combo count while one is going. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        long age = mc.level.getGameTime() - comboShownAt;
        if (combo > 0 && age < 30) {
            int alpha = (int) (255 * Math.max(0.2, 1 - age / 30.0));
            float scale = combo >= 4 ? 1.6F : 1.2F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + 12, y - 14, 0);
            graphics.pose().scale(scale, scale, 1);
            int colour = combo >= 4 ? 0xFFD24A : 0xFFFFFF;
            graphics.drawString(mc.font, Component.literal("x" + combo + (combo >= 4 ? "!" : "")), 0, 0,
                    (alpha << 24) | colour, true);
            graphics.pose().popPose();
        }
        graphics.drawCenteredString(mc.font, Component.translatable("tensurafragments.combat.hud"), x, y + 10, 0x80FFFFFF);
    }
}
