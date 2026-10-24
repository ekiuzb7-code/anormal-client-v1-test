package com.lucidclient.mixin;

import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation) Full Bright: while the camera is
 * detached, every light level reports full brightness so caves explored in
 * freecam are visible.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Inject(method = "getBrightness(Lnet/minecraft/world/dimension/DimensionType;I)F", at = @At("HEAD"), cancellable = true)
    private static void lucid$freecamBright(DimensionType type, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (Freecam.isActive() && Freecam.isFullBright()) {
            cir.setReturnValue(1.0f);
        }
    }
}
