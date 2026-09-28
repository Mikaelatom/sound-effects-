package com.tensurafragments.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tensurafragments.ModRegistries;
import com.tensurafragments.TensuraFragments;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = TensuraFragments.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final String CATEGORY = "key.categories.tensurafragments";

    public static final KeyMapping THROW_CARD = new KeyMapping("key.tensurafragments.throw_card",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping TELEPORT_CARD = new KeyMapping("key.tensurafragments.teleport_card",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping DETONATE_CARDS = new KeyMapping("key.tensurafragments.detonate_cards",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(THROW_CARD);
        event.register(TELEPORT_CARD);
        event.register(DETONATE_CARDS);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.CARD.get(), CardRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(TensuraFragments.id("card_hud"), CardHud::render);
    }
}
