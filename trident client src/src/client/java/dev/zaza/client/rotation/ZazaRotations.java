package dev.zaza.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Static bridge between the mixins and the {@link RotationManager}, standing in
 * for Cold Client's event bus.
 */
public final class ZazaRotations {
    private static RotationManager manager;

    private ZazaRotations() {}

    public static void setManager(RotationManager rotationManager) {
        manager = rotationManager;
    }

    public static RotationManager manager() {
        return manager;
    }

    public static void turn(Minecraft client, double seconds) {
        if (manager != null) manager.onTurn(client, (float) (seconds * 20.0));
    }

    public static float sentYaw(float actual) {
        return manager != null ? manager.sentYaw(actual) : actual;
    }

    public static float sentPitch(float actual) {
        return manager != null ? manager.sentPitch(actual) : actual;
    }

    public static void sent(Rotation rotation) {
        if (manager != null) manager.sent(rotation);
    }

    public static Vec3 viewVector(Entity entity) {
        return manager != null ? manager.viewVector(entity) : null;
    }

    public static void sync(Rotation rotation) {
        if (manager != null) manager.sync(rotation);
    }
}
