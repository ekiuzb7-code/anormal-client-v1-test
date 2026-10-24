package com.lucidclient.freecam;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.ChunkPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A saved freecam camera position (position + rotation), ported from the
 * Freecam mod. Carries the camera basis vectors so the perspective offsets
 * (first person / third person pullback) can move relative to the view.
 */
public final class FreecamPosition {
    public double x;
    public double y;
    public double z;
    public float pitch;
    public float yaw;

    private final Quaternionf rotation = new Quaternionf(0.0F, 0.0F, 0.0F, 1.0F);
    private final Vector3f verticalPlane = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f diagonalPlane = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f horizontalPlane = new Vector3f(0.0F, 0.0F, 1.0F);

    public FreecamPosition(Entity entity) {
        x = entity.getX();
        y = swimmingY(entity);
        z = entity.getZ();
        setRotation(entity.getYaw(), entity.getPitch());
    }

    /** From net.minecraft.client.render.Camera.setRotation. */
    public void setRotation(float yaw, float pitch) {
        this.pitch = pitch;
        this.yaw = yaw;
        rotation.rotationYXZ(-yaw * ((float) Math.PI / 180), pitch * ((float) Math.PI / 180), 0.0f);
        horizontalPlane.set(0.0f, 0.0f, 1.0f);
        verticalPlane.set(0.0f, 1.0f, 0.0f);
        diagonalPlane.set(1.0f, 0.0f, 0.0f);
        horizontalPlane.rotate(rotation);
        verticalPlane.rotate(rotation);
        diagonalPlane.rotate(rotation);
    }

    /** Invert the rotation so the view is mirrored (third-person front). */
    public void mirrorRotation() {
        setRotation(yaw + 180.0F, -pitch);
    }

    public void moveForward(double distance) {
        move(distance, 0, 0);
    }

    /** From net.minecraft.client.render.Camera.moveBy. */
    public void move(double fwd, double up, double right) {
        x += (double) horizontalPlane.x() * fwd
                + (double) verticalPlane.x() * up
                + (double) diagonalPlane.x() * right;
        y += (double) horizontalPlane.y() * fwd
                + (double) verticalPlane.y() * up
                + (double) diagonalPlane.y() * right;
        z += (double) horizontalPlane.z() * fwd
                + (double) verticalPlane.z() * up
                + (double) diagonalPlane.z() * right;
    }

    public ChunkPos getChunkPos() {
        return new ChunkPos((int) (x / 16), (int) (z / 16));
    }

    private static double swimmingY(Entity entity) {
        if (entity.getPose() == EntityPose.SWIMMING) {
            return entity.getY();
        }
        return entity.getY() - entity.getEyeHeight(EntityPose.SWIMMING) + entity.getEyeHeight(entity.getPose());
    }
}
