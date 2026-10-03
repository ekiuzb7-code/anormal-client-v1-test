package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

    // FreeLook: Override BOTH position and rotation at HEAD (cancel normal update)
    // Calculate third-person offset using CAMERA rotation (lookYaw/lookPitch), not player body rotation
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCameraUpdateFreeLook(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        try {
            FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive() && focusedEntity != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                // Use camera rotation (lookYaw/lookPitch) for everything
                float camYaw = freeLook.getLookYaw();
                float camPitch = freeLook.getLookPitch();

                // Calculate camera position based on perspective and CAMERA rotation
                Vec3d eyePos = focusedEntity.getEyePos();
                double camX = eyePos.x;
                double camY = eyePos.y;
                double camZ = eyePos.z;

                if (thirdPerson) {
                    // Replicate Camera.calculateThirdPersonPosition logic but with camera rotation
                    // Use fallback distance since getThirdPersonDistance() mapping varies
                    double distance = 4.0; // Default third person distance
                    try {
                        // Try to get from options (mapping varies by version)
                        distance = mc.options.getFov().getValue(); // fallback to FOV if needed
                    } catch (Throwable ignored) {}
                    if (inverseView) distance *= -1.0;

                    // Use CAMERA yaw/pitch for offset calculation
                    float yawRad = (float) Math.toRadians(camYaw);
                    float pitchRad = (float) Math.toRadians(camPitch);

                    double offsetX = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad) * distance;
                    double offsetY = -MathHelper.sin(pitchRad) * distance;
                    double offsetZ = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad) * distance;

                    camX += offsetX;
                    camY += offsetY;
                    camZ += offsetZ;

                    // Front/back perspective flip
                    Perspective perspective = mc.options.getPerspective();
                    if (perspective == Perspective.THIRD_PERSON_FRONT) {
                        // Camera in front looking at player - flip offset
                        camX = eyePos.x - offsetX;
                        camY = eyePos.y - offsetY;
                        camZ = eyePos.z - offsetZ;
                    }
                }

                setPos(camX, camY, camZ);
                setRotation(camYaw, camPitch);
                ci.cancel();
                return;
            }

            // Uzny11 FreeLook
            com.anormal.client.module.impl.uzny11.FreeLook uLook =
                    ModuleManager.getModule(com.anormal.client.module.impl.uzny11.FreeLook.class);
            if (uLook != null && uLook.isEnabled() && uLook.isCameraActive() && focusedEntity != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                float camYaw = uLook.getLookYaw();
                float camPitch = uLook.getLookPitch();

                Vec3d eyePos = focusedEntity.getEyePos();
                double camX = eyePos.x;
                double camY = eyePos.y;
                double camZ = eyePos.z;

                if (thirdPerson) {
                    double distance = 4.0; // Default third person distance
                    try {
                        distance = mc.options.getFov().getValue();
                    } catch (Throwable ignored) {}
                    if (inverseView) distance *= -1.0;

                    float yawRad = (float) Math.toRadians(camYaw);
                    float pitchRad = (float) Math.toRadians(camPitch);

                    double offsetX = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad) * distance;
                    double offsetY = -MathHelper.sin(pitchRad) * distance;
                    double offsetZ = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad) * distance;

                    camX += offsetX;
                    camY += offsetY;
                    camZ += offsetZ;

                    Perspective perspective = mc.options.getPerspective();
                    if (perspective == Perspective.THIRD_PERSON_FRONT) {
                        camX = eyePos.x - offsetX;
                        camY = eyePos.y - offsetY;
                        camZ = eyePos.z - offsetZ;
                    }
                }

                setPos(camX, camY, camZ);
                setRotation(camYaw, camPitch);
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
