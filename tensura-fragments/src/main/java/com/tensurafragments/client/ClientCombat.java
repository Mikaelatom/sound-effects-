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

/**
 * Combat Mode on the client: its keys (G to toggle, H to block, J to grab and throw, K to dash), the slam and uppercut
 * inputs, the dash's movement, and the combo counter by the crosshair.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class ClientCombat {
    public static final KeyMapping TOGGLE = new KeyMapping("key.tensurafragments.combat_mode", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.tensurafragments");
    public static final KeyMapping BLOCK = new KeyMapping("key.tensurafragments.combat_block", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H, "key.categories.tensurafragments");
    public static final KeyMapping GRAB = new KeyMapping("key.tensurafragments.combat_grab", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.tensurafragments");
    public static final KeyMapping DASH = new KeyMapping("key.tensurafragments.combat_dash", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.tensurafragments");
    public static final KeyMapping STYLE = new KeyMapping("key.tensurafragments.combat_style", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, "key.categories.tensurafragments");
    /** A dash lasts a few ticks of fast movement. */
    private static final int DASH_TICKS = 4;
    private static final double DASH_SPEED = 0.9;
    private static final int DASH_COOLDOWN = 20;
    /** The Explosion style's burst dash speed (blocks a tick, any direction). */
    private static final double BURST_SPEED = 1.2;
    private static boolean burst;
    /** Ticks spent hovering on blasts since you last touched the ground. */
    private static int hoverTicks;
    /** How long you can hover before you have to land (ticks). */
    private static final int HOVER_LIMIT = 100;

    /**
     * The Explosion style's flight: hold jump in mid-air and blasts from your palms hold you up and carry you the way
     * you look, for up to 5 seconds before you have to land again.
     */
    private static void hover(Minecraft mc, boolean able) {
        if (mc.player == null) {
            return;
        }
        if (mc.player.onGround() || mc.player.isInWater()) {
            hoverTicks = 0;
            return;
        }
        if (!able || style != com.tensurafragments.combat.FightingStyle.EXPLOSION || !mc.options.keyJump.isDown()
                || mc.player.getAbilities().flying || mc.player.isPassenger() || hoverTicks >= HOVER_LIMIT) {
            return;
        }
        hoverTicks++;
        net.minecraft.world.phys.Vec3 motion = mc.player.getDeltaMovement();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1);
        double x = motion.x + look.x * 0.06;
        double z = motion.z + look.z * 0.06;
        double speed = Math.sqrt(x * x + z * z);
        if (speed > 0.8) {
            x *= 0.8 / speed;
            z *= 0.8 / speed;
        }
        mc.player.setDeltaMovement(x, Math.max(motion.y, 0.22), z);
        mc.player.fallDistance = 0;
        if (hoverTicks % 4 == 1) {
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.HOVER));
        }
    }
    private static boolean on;
    private static boolean blocking;
    private static int dashTicks;
    private static long lastDash = -100;
    private static net.minecraft.world.phys.Vec3 dashDirection = net.minecraft.world.phys.Vec3.ZERO;
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

    public static boolean isBlocking() {
        return blocking;
    }

    private static com.tensurafragments.combat.FightingStyle style = com.tensurafragments.combat.FightingStyle.BRAWLER;
    /** Down power of what you last hit, and when it was shown. */
    private static int down;
    private static long downShownAt;

    public static com.tensurafragments.combat.FightingStyle style() {
        return style;
    }

    public static void sync(boolean isOn, int count, int styleId, int downPower) {
        on = isOn;
        combo = count;
        style = com.tensurafragments.combat.FightingStyle.byId(styleId);
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        comboShownAt = now;
        if (downPower > 0) {
            down = downPower;
            downShownAt = now;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (mc.player != null) {
                PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.TOGGLE));
            }
        }
        boolean able = on && mc.player != null && mc.level != null && mc.screen == null && !ClientPossession.isPossessing()
                && mc.player.isAlive();
        // Blocking: while the key is held.
        boolean wantBlock = able && BLOCK.isDown() && dashTicks == 0;
        if (wantBlock != blocking) {
            blocking = wantBlock;
            PacketDistributor.sendToServer(new CombatInputPayload(
                    blocking ? CombatInputPayload.BLOCK_START : CombatInputPayload.BLOCK_STOP));
        }
        while (GRAB.consumeClick()) {
            if (able) {
                PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.GRAB));
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
        while (STYLE.consumeClick()) {
            if (mc.player != null) {
                PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.STYLE));
            }
        }
        while (DASH.consumeClick()) {
            if (able) {
                startDash(mc);
            }
        }
        if (dashTicks > 0 && mc.player != null) {
            dashTicks--;
            net.minecraft.world.phys.Vec3 motion = mc.player.getDeltaMovement();
            if (burst) {
                mc.player.setDeltaMovement(dashDirection.scale(BURST_SPEED));
            } else {
                // Along the ground, or straight across in mid-air.
                mc.player.setDeltaMovement(dashDirection.x * DASH_SPEED, mc.player.onGround() ? motion.y : Math.max(motion.y, 0),
                        dashDirection.z * DASH_SPEED);
            }
            if (dashTicks == 0) {
                burst = false;
            }
        }
        hover(mc, able);
    }

    /** Dash the way you're moving (forward if you aren't). */
    private static void startDash(Minecraft mc) {
        long now = mc.level.getGameTime();
        if (style == com.tensurafragments.combat.FightingStyle.TITAN || style == com.tensurafragments.combat.FightingStyle.KI) {
            // Iron body and vanish: the server does it all.
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.DASH));
            return;
        }
        boolean swift = style == com.tensurafragments.combat.FightingStyle.SWIFT;
        boolean explosive = style == com.tensurafragments.combat.FightingStyle.EXPLOSION;
        if (now - lastDash < (swift ? 8 : explosive ? 12 : DASH_COOLDOWN) || mc.player.isPassenger()
                || mc.player.getAbilities().flying) {
            return;
        }
        if (explosive) {
            // The burst dash: blasted whichever way you look, up and down included.
            lastDash = now;
            dashDirection = mc.player.getLookAngle();
            dashTicks = DASH_TICKS + 1;
            burst = true;
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.DASH, (float) dashDirection.x,
                    (float) dashDirection.z));
            return;
        }
        lastDash = now;
        float strafe = mc.player.input.leftImpulse;
        float forward = mc.player.input.forwardImpulse;
        if (Math.abs(strafe) < 0.01F && Math.abs(forward) < 0.01F) {
            forward = 1;
        }
        double yaw = Math.toRadians(mc.player.getYRot());
        double x = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double z = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        dashDirection = new net.minecraft.world.phys.Vec3(x, 0, z).normalize();
        dashTicks = swift ? DASH_TICKS - 1 : DASH_TICKS;
        PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.DASH, (float) dashDirection.x,
                (float) dashDirection.z));
    }

    /** Blocking slows you to a shuffle. */
    @SubscribeEvent
    public static void onMovementInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        if (blocking) {
            event.getInput().forwardImpulse *= 0.3F;
            event.getInput().leftImpulse *= 0.3F;
            event.getEntity().setSprinting(false);
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
        if (blocking) {
            // Can't punch with your guard up.
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (!mc.player.onGround() && mc.player.isShiftKeyDown() && !mc.player.isInWater()) {
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.SLAM));
            event.setSwingHand(true);
            event.setCanceled(true);
            return;
        }
        boolean hovering = style == com.tensurafragments.combat.FightingStyle.EXPLOSION && hoverTicks > 0
                && mc.options.keyJump.isDown();
        if (!mc.player.onGround() && mc.player.getDeltaMovement().y > 0 && !mc.player.isInWater()
                && !mc.player.getAbilities().flying && !hovering) {
            // Sent before the attack itself, so the server knows this punch is the uppercut.
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.UPPERCUT));
            if (style == com.tensurafragments.combat.FightingStyle.BRAWLER) {
                CombatAnimDriver.play(mc.player, com.tensurafragments.combat.CombatMode.UPPERCUT_ANIMATION);
            }
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
        int finisher = style.finisherHit();
        if (combo > 0 && age < 30) {
            int alpha = (int) (255 * Math.max(0.2, 1 - age / 30.0));
            float scale = combo >= finisher ? 1.6F : 1.2F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + 12, y - 14, 0);
            graphics.pose().scale(scale, scale, 1);
            int colour = combo >= finisher ? 0xFFD24A : 0xFFFFFF;
            graphics.drawString(mc.font, Component.literal("x" + combo + (combo >= finisher ? "!" : "")), 0, 0,
                    (alpha << 24) | colour, true);
            graphics.pose().popPose();
        }
        // Down power of what you're hitting: a bar that fills; full is a knockdown.
        long downAge = mc.level.getGameTime() - downShownAt;
        if (down > 0 && downAge < 40) {
            int left = x + 12;
            int top = y - 2;
            int width = 34;
            graphics.fill(left - 1, top - 1, left + width + 1, top + 4, 0x90000000);
            int filled = Math.round(width * Math.min(100, down) / 100F);
            graphics.fill(left, top, left + filled, top + 3, down >= 100 ? 0xFFFF5050 : 0xFFFFFFFF);
            if (down >= 100) {
                graphics.drawString(mc.font, Component.translatable("tensurafragments.combat.down_hud"), left, top + 6,
                        0xFFFF5050, true);
            }
        }
        Component label = blocking ? Component.translatable("tensurafragments.combat.hud_blocking")
                : Component.translatable("tensurafragments.combat.hud_style", Component.translatable(style.translationKey()));
        graphics.drawCenteredString(mc.font, label, x, y + 10, blocking ? 0xC0FFE08A : 0x80FFFFFF);
    }
}
