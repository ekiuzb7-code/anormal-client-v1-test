package com.anormal.client.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Aim points on a living entity, ported from Cold Client.
 *
 * <p>The offsets are relative to the entity's feet and rotated by its body yaw, so a target that is
 * walking sideways still gets hit where it actually is. {@link #MULTIPOINT} ignores the fixed
 * offsets and instead clamps the look ray into the hitbox, which is what makes aiming feel
 * "magnetic" at close range.</p>
 */
public enum Bone {
    MULTIPOINT("Multipoint", null),
    HEAD("Head", new Vec3d(0.0, 1.75, 0.0)),
    BODY("Body", new Vec3d(0.0, 1.125, 0.0)),
    LEFT_ARM("Left Arm", new Vec3d(0.375, 1.125, 0.0)),
    RIGHT_ARM("Right Arm", new Vec3d(-0.375, 1.125, 0.0)),
    LEFT_LEG("Left Leg", new Vec3d(0.125, 0.375, 0.0)),
    RIGHT_LEG("Right Leg", new Vec3d(-0.125, 0.375, 0.0));

    public static final Bone[] ALL = values();
    public static final String[] NAMES = {
            "Multipoint", "Head", "Body", "Left Arm", "Right Arm", "Left Leg", "Right Leg"
    };

    private static final double MARGIN = 0.1;
    private static final double MARGIN_FRACTION = 0.25;
    private static final int REFINEMENTS = 2;

    private final String label;
    private final Vec3d center;

    Bone(String label, Vec3d center) {
        this.label = label;
        this.center = center;
    }

    public String label() {
        return label;
    }

    public static Bone byLabel(String label) {
        for (Bone bone : ALL) {
            if (bone.label.equalsIgnoreCase(label)) return bone;
        }
        return HEAD;
    }

    /** World-space aim point for this bone. */
    public Vec3d point(LivingEntity viewer, LivingEntity target) {
        Vec3d position = new Vec3d(target.getX(), target.getY(), target.getZ());
        if (center == null) {
            return closest(viewer.getEyePos(), viewer.getRotationVec(1.0f), target.getBoundingBox());
        }

        double angle = Math.toRadians(target.getBodyYaw());
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        Vec3d offset = center.multiply(target.getScale());
        return position.add(
                offset.x * cos - offset.z * sin,
                offset.y,
                offset.x * sin + offset.z * cos);
    }

    /** Clamp the look ray into a slightly shrunk hitbox - the classic "multipoint" behaviour. */
    static Vec3d closest(Vec3d eye, Vec3d look, Box box) {
        Box inner = box.contract(
                margin(box.getLengthX()), margin(box.getLengthY()), margin(box.getLengthZ()));
        Vec3d point = inner.getCenter();

        for (int i = 0; i < REFINEMENTS; i++) {
            double depth = Math.max(0.0, point.subtract(eye).dotProduct(look));
            Vec3d along = eye.add(look.multiply(depth));
            point = new Vec3d(
                    MathHelper.clamp(along.x, inner.minX, inner.maxX),
                    MathHelper.clamp(along.y, inner.minY, inner.maxY),
                    MathHelper.clamp(along.z, inner.minZ, inner.maxZ));
        }
        return point;
    }

    private static double margin(double size) {
        return Math.min(MARGIN, size * MARGIN_FRACTION);
    }
}
