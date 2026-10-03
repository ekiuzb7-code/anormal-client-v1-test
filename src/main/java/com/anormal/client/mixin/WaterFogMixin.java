package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.ClearWater;
import net.minecraft.client.render.WaterFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterFog.class)
public class WaterFogMixin {

    @Inject(method = "getFogStart", at = @At("HEAD"), cancellable = true, require = 0)
    private static void onGetFogStart(CallbackInfoReturnable<Integer> cir) {
        try {
            ClearWater cw = ModuleManager.getModule(ClearWater.class);
            if (cw != null && cw.isEnabled() && cw.removeWaterFog.isEnabled()) {
                cir.setReturnValue(0);
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "getFogEnd", at = @At("HEAD"), cancellable = true, require = 0)
    private static void onGetFogEnd(CallbackInfoReturnable<Integer> cir) {
        try {
            ClearWater cw = ModuleManager.getModule(ClearWater.class);
            if (cw != null && cw.isEnabled() && cw.removeWaterFog.isEnabled()) {
                cir.setReturnValue(0);
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void onGetFogColor(CallbackInfoReturnable<Integer> cir) {
        try {
            ClearWater cw = ModuleManager.getModule(ClearWater.class);
            if (cw != null && cw.isEnabled() && cw.removeWaterFog.isEnabled()) {
                cir.setReturnValue(0x00FFFFFF); // Transparent
            }
        } catch (Throwable ignored) {}
    }
}