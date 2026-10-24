package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.freecam.FreeCamera;
import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation) visibility rules: the FreeCamera
 * entity itself is never rendered, and the real player body is hidden when
 * Show Player is off.
 */
@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (entity instanceof FreeCamera) {
                cir.setReturnValue(false);
            } else if (entity == MinecraftClient.getInstance().player && Freecam.isActive() && !Freecam.shouldShowPlayer()) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid EntityRenderManagerMixin threw", t);
        }
    }
}
