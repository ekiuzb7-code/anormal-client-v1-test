package com.anormal.client.rotation;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A yaw/pitch pair.
 *
 * <p>Ported from Cold Client's rotation system (via trident/zaza.hook). The point of keeping it as
 * a record is that every rotation computation stays pure - the modules decide whether the result
 * goes to the camera (visible) or only to the server (silent).</p>
 */
public record Rotation(float yaw, float pitch) {

    public static Rotation of(Entity entity) {
        return new Rotation(entity.getYaw(), entity.getPitch());
    }

    /** Rotation that looks from {@code from} towards {@code to}. */
    public static Rotation toward(Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return new Rotation(yaw, pitch);
    }

    /** Unit vector this rotation points at. */
    public Vec3d direction() {
        return Vec3d.fromPolar(pitch, yaw);
    }

    public float yawTo(Rotation target) {
        return MathHelper.wrapDegrees(target.yaw - yaw);
    }

    public float pitchTo(Rotation target) {
        return MathHelper.clamp(target.pitch, -90.0f, 90.0f) - pitch;
    }

    public float distanceTo(Rotation target) {
        return length(yawTo(target), pitchTo(target));
    }

    public Rotation moved(float deltaYaw, float deltaPitch) {
        return new Rotation(yaw + deltaYaw, MathHelper.clamp(pitch + deltaPitch, -90.0f, 90.0f));
    }

    public static float length(float a, float b) {
        return (float) Math.sqrt(a * a + b * b);
    }

    /** Angle in degrees between two unit vectors. */
    public static double angle(Vec3d a, Vec3d b) {
        double dot = a.normalize().dotProduct(b.normalize());
        return Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
    }
}
