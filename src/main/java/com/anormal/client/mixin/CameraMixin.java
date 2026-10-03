package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {

    @Shadow
    protected void setPos(double x, double y, double z) {}

    @Shadow
    protected void setRotation(float yaw, float pitch) {}

    // Freecam: Full camera control (position + rotation) - inject at HEAD and cancel
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCameraUpdateFreecam(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        try {
            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                setPos(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
                setRotation(freecam.getCamYaw(), freecam.getCamPitch());
                ci.cancel();
                return;
            }
            // Uzny11 Freecam
            com.anormal.client.module.impl.uzny11.Freecam uCam =
                    ModuleManager.getModule(com.anormal.client.module.impl.uzny11.Freecam.class);
            if (uCam != null && uCam.isEnabled() && uCam.isCameraActive()) {
                setPos(uCam.getCamX(), uCam.getCamY(), uCam.getCamZ());
                setRotation(uCam.getCamYaw(), uCam.getCamPitch());
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) {}
    }

    // FreeLook: Only override rotation (let Camera class handle third-person position offset)
    // Inject at RETURN so normal position calculation runs first, then we override rotation
    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void onCameraUpdateFreeLook(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        try {
            FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive() && focusedEntity != null) {
                setRotation(freeLook.getLookYaw(), freeLook.getLookPitch());
                return;
            }
            // Uzny11 FreeLook
            com.anormal.client.module.impl.uzny11.FreeLook uLook =
                    ModuleManager.getModule(com.anormal.client.module.impl.uzny11.FreeLook.class);
            if (uLook != null && uLook.isEnabled() && uLook.isCameraActive() && focusedEntity != null) {
                setRotation(uLook.getLookYaw(), uLook.getLookPitch());
            }
        } catch (Throwable ignored) {}
    }
}
