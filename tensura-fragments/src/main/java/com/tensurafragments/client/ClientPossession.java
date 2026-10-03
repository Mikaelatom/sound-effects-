package com.tensurafragments.client;

import com.tensurafragments.TensuraFragments;
import com.tensurafragments.network.BeastInputPayload;
import com.tensurafragments.shikigami.BeastKind;
import com.tensurafragments.shikigami.PaperBeastEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Seeing through a paper beast: the camera moves into it, your movement keys and mouse steer it, and your attack is
 * its attack. Your own body stands still meanwhile.
 */
@EventBusSubscriber(modid = TensuraFragments.MODID, value = Dist.CLIENT)
public final class ClientPossession {
    private static int beastId = -1;
    private static int waitingTicks;
    private static float forward;
    private static float strafe;
    private static boolean jump;
    private static boolean down;
    private static boolean attack;
    /** Where your body was left, to hold it there once the world around it is no longer sent to you. */
    @Nullable
    private static Vec3 body;

    private ClientPossession() {
    }

    public static boolean isPossessing() {
        return beastId >= 0;
    }

    /** From the server: see through this beast (-1: back to your body). */
    public static void possess(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        beastId = entityId;
        waitingTicks = 0;
        attack = false;
        body = entityId >= 0 && mc.player != null ? mc.player.position() : null;
        if (entityId < 0) {
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
            return;
        }
        Entity beast = mc.level == null ? null : mc.level.getEntity(entityId);
        if (beast != null) {
            mc.setCameraEntity(beast);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (beastId < 0 || mc.player == null || mc.level == null) {
            return;
        }
        Entity beast = mc.level.getEntity(beastId);
        if (beast == null) {
            // It may not have reached us yet; give it a moment, then fall back to our own eyes.
            if (++waitingTicks > 40 && mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
        } else if (mc.getCameraEntity() != beast) {
            mc.setCameraEntity(beast);
        }
        if (body != null && !mc.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(body.x),
                SectionPos.blockToSectionCoord(body.z))) {
            // The beast is far away, so the world is sent around it instead: keep your body from falling through
            // the missing ground.
            mc.player.setPos(body.x, body.y, body.z);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.resetFallDistance();
        }
        byte keys = (byte) ((jump ? BeastInputPayload.JUMP : 0) | (down ? BeastInputPayload.DOWN : 0)
                | (attack ? BeastInputPayload.ATTACK : 0));
        attack = false;
        PacketDistributor.sendToServer(new BeastInputPayload(forward, strafe, keys, mc.player.getYRot(), mc.player.getXRot()));
    }

    /** Your movement keys steer the beast, and your body stays put. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (beastId < 0) {
            return;
        }
        Input input = event.getInput();
        forward = input.forwardImpulse;
        strafe = input.leftImpulse;
        jump = input.jumping;
        down = input.shiftKeyDown;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** Attack is the beast's attack; using and picking items do nothing while you're away from your body. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (beastId < 0) {
            return;
        }
        if (event.isAttack()) {
            attack = true;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /** Look where your mouse points right away, rather than waiting for the server to turn the beast. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (beastId >= 0 && mc.player != null && mc.getCameraEntity() instanceof PaperBeastEntity) {
            float partial = (float) event.getPartialTick();
            event.setYaw(mc.player.getViewYRot(partial));
            event.setPitch(mc.player.getViewXRot(partial));
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (beastId >= 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        beastId = -1;
    }

    /** Top of the screen while possessing: which beast, and the cat's glide gauge. */
    static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (beastId < 0 || mc.level == null || mc.options.hideGui
                || !(mc.level.getEntity(beastId) instanceof PaperBeastEntity beast)) {
            return;
        }
        BeastKind kind = beast.getKind();
        Component text = Component.translatable("tensurafragments.beast.possessing",
                Component.translatable("tensurafragments.beast." + kind.id()));
        int width = mc.font.width(text);
        int x = (graphics.guiWidth() - width) / 2;
        graphics.drawString(mc.font, text, x, 8, 0xFFF4EFE0, true);
        float health = beast.getHealth() / beast.getMaxHealth();
        graphics.fill(x, 19, x + width, 21, 0x60000000);
        graphics.fill(x, 19, x + Math.round(width * health), 21, 0xFF7CE07C);
        if (kind == BeastKind.CAT) {
            graphics.fill(x, 22, x + Math.round(width * beast.glideFraction()), 23, 0xFF9FD8FF);
        }
    }
}
