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
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$musicClick(long window, net.minecraft.client.input.MouseInput input,
                                    int action, CallbackInfo ci) {
        try {
            if (action != 1) return;
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.currentScreen != null) return;

            com.anormal.client.module.impl.legit.MusicPlayer player =
                    ModuleManager.getModule(com.anormal.client.module.impl.legit.MusicPlayer.class);
            if (player == null || !player.isEnabled()) return;

            double[] cx = new double[1];
            double[] cy = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, cx, cy);
            double scale = client.getWindow().getScaleFactor();

            // The click is cancelled so pressing play does not also swing at the world.
            if (player.handleClick(cx[0] / scale, cy[0] / scale, input.button())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

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
