package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.Zoom;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        try {
            Zoom zoom = ModuleManager.getModule(Zoom.class);
            if (zoom != null && zoom.isControllingScroll() && vertical != 0) {
                zoom.onMouseScroll(vertical);
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
