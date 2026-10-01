package com.tensurafragments.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tensurafragments.client.ClientEnergy;
import com.tensurafragments.client.ClientRainbow;
import com.tensurafragments.client.EnergyBufferSource;
import com.tensurafragments.client.RainbowBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws Rainbow Magic and Energy Magic spells (Tensura's own spell entities) through a recolouring buffer. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    public abstract <E extends Entity> void render(E entity, double x, double y, double z, float rotationYaw,
                                                   float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                                                   int packedLight);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void tensurafragments$rainbow(E entity, double x, double y, double z, float rotationYaw,
                                                              float partialTicks, PoseStack poseStack,
                                                              MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(buffer instanceof EnergyBufferSource) && ClientEnergy.isEnergy(entity)) {
            if (entity.tickCount % 10 == 0) {
                org.slf4j.LoggerFactory.getLogger("visualcheck").warn("CLIENT energy render {} {}", entity.getId(), entity.getType());
            }
            ci.cancel();
            render(entity, x, y, z, rotationYaw, partialTicks, poseStack, new EnergyBufferSource(buffer),
                    LightTexture.FULL_BRIGHT);
            return;
        }
        if (!(buffer instanceof RainbowBufferSource) && ClientRainbow.isRainbow(entity)) {
            ci.cancel();
            render(entity, x, y, z, rotationYaw, partialTicks, poseStack,
                    new RainbowBufferSource(buffer, ClientRainbow.hue(entity, partialTicks)), packedLight);
        }
    }
}
