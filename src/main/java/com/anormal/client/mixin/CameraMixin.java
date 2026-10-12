package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the Freecam / FreeLook camera transform.
 *
 * <p>We inject at {@code RETURN} instead of cancelling at {@code HEAD}.
 * {@code Camera.update()} is what sets the private {@code ready} flag, and a
 * HEAD cancel left the camera permanently "not ready" so the world was not
 * rendered at all. Letting vanilla run and then overwriting the final
 * position/rotation keeps every internal field consistent.</p>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At("RETURN"), require = 0)
    private void anormal$update(World area, Entity focusedEntity, boolean thirdPerson,
                                boolean inverseView, float tickProgress, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            // ---- Freecam: full camera control (position + rotation) ----
            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                setPos(freecam.getInterpolatedX(tickProgress),
                        freecam.getInterpolatedY(tickProgress),
                        freecam.getInterpolatedZ(tickProgress));
                setRotation(freecam.getCamYaw(), freecam.getCamPitch());
                return;
            }

            // ---- FreeLook: third-person orbit driven by the detached look ----
            FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive()) {
                if (!thirdPerson) return;

                float yaw = freeLook.getLookYaw();
                float pitch = freeLook.getLookPitch();

                Vec3d eye = focusedEntity != null ? focusedEntity.getEyePos() : mc.player.getEyePos();

                // Standard Minecraft look vector for (yaw, pitch).
                double yawRad = Math.toRadians(yaw);
                double pitchRad = Math.toRadians(pitch);
                double cosP = Math.cos(pitchRad);
                double lx = -Math.sin(yawRad) * cosP;
                double ly = -Math.sin(pitchRad);
                double lz = Math.cos(yawRad) * cosP;

                // Behind the player for back view, in front for the inverted view.
                double dist = inverseView ? 4.0 : -4.0;
                Vec3d camPos = eye.add(lx * dist, ly * dist, lz * dist);

                setPos(camPos.x, camPos.y, camPos.z);
                setRotation(yaw, pitch);
            }
        } catch (Throwable ignored) {
        }
    }
}
