package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the mouse-look so Freecam and FreeLook can rotate the camera
 * without rotating the player's body.
 *
 * <p>Vanilla's {@code Entity.changeLookDirection(deltaX, deltaY)} is called by
 * {@code Mouse} on every cursor movement and applies {@code delta * 0.15} to the
 * player's yaw/pitch. While a camera module is active we redirect that delta to
 * the module and cancel the vanilla rotation, which gives a real, per-frame
 * smooth detached look instead of the old "read yaw once per tick" hack.</p>
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$changeLookDirection(double deltaX, double deltaY, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || (Object) this != mc.player) return;

            // Same factor vanilla uses internally.
            double yawDelta = deltaX * 0.15;
            double pitchDelta = deltaY * 0.15;

            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                freecam.addLook(yawDelta, pitchDelta);
                ci.cancel();
                return;
            }

            FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive()) {
                freeLook.addLook(yawDelta, pitchDelta);
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
