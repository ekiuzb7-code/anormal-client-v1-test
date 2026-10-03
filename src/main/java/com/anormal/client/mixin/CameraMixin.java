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

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCameraUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        try {
            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                setPos(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
                setRotation(freecam.getCamYaw(), freecam.getCamPitch());
                ci.cancel();
                return;
            }
            FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive() && focusedEntity != null) {
                try {
                    setPos(focusedEntity.getEyePos().x, focusedEntity.getEyePos().y, focusedEntity.getEyePos().z);
                } catch (Throwable ignored) {}
                setRotation(freeLook.getLookYaw(), freeLook.getLookPitch());
                ci.cancel();
                return;
            }
            // Uzny11 twins (independent instances, same camera control)
            com.anormal.client.module.impl.uzny11.Freecam uCam =
                    ModuleManager.getModule(com.anormal.client.module.impl.uzny11.Freecam.class);
            if (uCam != null && uCam.isEnabled() && uCam.isCameraActive()) {
                setPos(uCam.getCamX(), uCam.getCamY(), uCam.getCamZ());
                setRotation(uCam.getCamYaw(), uCam.getCamPitch());
                ci.cancel();
                return;
            }
            com.anormal.client.module.impl.uzny11.FreeLook uLook =
                    ModuleManager.getModule(com.anormal.client.module.impl.uzny11.FreeLook.class);
            if (uLook != null && uLook.isEnabled() && uLook.isCameraActive() && focusedEntity != null) {
                try {
                    setPos(focusedEntity.getEyePos().x, focusedEntity.getEyePos().y, focusedEntity.getEyePos().z);
                } catch (Throwable ignored) {}
                setRotation(uLook.getLookYaw(), uLook.getLookPitch());
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
