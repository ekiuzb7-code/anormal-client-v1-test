package com.lucidclient.freecam;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Camera movement for the freecam entity (DEFAULT flight mode), ported from
 * the Freecam mod: WASD relative to the camera yaw, space/shift for vertical,
 * diagonal normalisation, sprint ×1.5.
 */
public final class FreecamMotion {
    private static final double DIAGONAL_MULTIPLIER = MathHelper.sin((float) Math.toRadians(45));

    private FreecamMotion() {}

    public static void doMotion(FreeCamera camera, double hSpeed, double vSpeed) {
        float yaw = camera.getYaw();
        double velocityX = 0.0;
        double velocityY = 0.0;
        double velocityZ = 0.0;

        Vec3d forward = Vec3d.fromPolar(0, yaw);
        Vec3d side = Vec3d.fromPolar(0, yaw + 90);

        hSpeed = hSpeed * (camera.isSprinting() ? 1.5 : 1.0);

        boolean straight = false;
        if (camera.input.playerInput.forward()) {
            velocityX += forward.x * hSpeed;
            velocityZ += forward.z * hSpeed;
            straight = true;
        }
        if (camera.input.playerInput.backward()) {
            velocityX -= forward.x * hSpeed;
            velocityZ -= forward.z * hSpeed;
            straight = true;
        }

        boolean strafing = false;
        if (camera.input.playerInput.right()) {
            velocityZ += side.z * hSpeed;
            velocityX += side.x * hSpeed;
            strafing = true;
        }
        if (camera.input.playerInput.left()) {
            velocityZ -= side.z * hSpeed;
            velocityX -= side.x * hSpeed;
            strafing = true;
        }

        if (straight && strafing) {
            velocityX *= DIAGONAL_MULTIPLIER;
            velocityZ *= DIAGONAL_MULTIPLIER;
        }

        if (camera.input.playerInput.jump()) {
            velocityY += vSpeed;
        }
        if (camera.input.playerInput.sneak()) {
            velocityY -= vSpeed;
        }

        camera.setVelocity(velocityX, velocityY, velocityZ);
    }
}
