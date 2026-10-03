package com.anormal.client.util;

import com.anormal.client.mixin.GameRendererMixin;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

// Single world→screen projector for every marker module.
// Uses the LOOK direction (player yaw/pitch, or detached camera angles in
// freecam/freelook) instead of Camera getters, plus the real effective FOV.
public final class ProjectionUtil {
    private ProjectionUtil() {}

    // Last tick's eye per session: render camera sits between ticks, so a
    // tick-frozen origin made markers swim. Tracked by value change (exact
    // displacement, no velocity involved, immune to acceleration).
    private static double lastEyeX, lastEyeY, lastEyeZ;
    private static double prevEyeX, prevEyeY, prevEyeZ;
    private static boolean eyeInit = false;

    // Per-entity previous centers for exact render-time interpolation.
    private static final java.util.Map<Integer, double[]> PREV = new java.util.HashMap<>();

    // Interpolated world position of an entity center for render time t.
    public static double[] lerpEntity(int id, double x, double y, double z, float t) {
        double[] prev = PREV.get(id);
        if (prev == null || Math.abs(prev[0] - x) > 32 || Math.abs(prev[1] - y) > 32 || Math.abs(prev[2] - z) > 32) {
            prev = new double[]{x, y, z};
            PREV.put(id, prev);
            if (PREV.size() > 300) PREV.clear();
        }
        double tt = Math.max(0.0, Math.min(1.0, t));
        double[] out = new double[]{
                prev[0] + (x - prev[0]) * tt,
                prev[1] + (y - prev[1]) * tt,
                prev[2] + (z - prev[2]) * tt
        };
        prev[0] = x;
        prev[1] = y;
        prev[2] = z;
        return out;
    }

    // Interpolated eye position for render time (shared by custom markers).
    public static Vec3d eye(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new Vec3d(0, 0, 0);
        try {
            Vec3d target = mc.player.getEyePos();
            if (!eyeInit || Math.abs(target.x - lastEyeX) > 64 || Math.abs(target.y - lastEyeY) > 64
                    || Math.abs(target.z - lastEyeZ) > 64) {
                prevEyeX = lastEyeX = target.x;
                prevEyeY = lastEyeY = target.y;
                prevEyeZ = lastEyeZ = target.z;
                eyeInit = true;
            }
            double tt = Math.max(0.0, Math.min(1.0, tickDelta));
            Vec3d out = new Vec3d(
                    prevEyeX + (target.x - prevEyeX) * tt,
                    prevEyeY + (target.y - prevEyeY) * tt,
                    prevEyeZ + (target.z - prevEyeZ) * tt);
            prevEyeX = lastEyeX = target.x;
            prevEyeY = lastEyeY = target.y;
            prevEyeZ = lastEyeZ = target.z;
            return out;
        } catch (Throwable t) {
            try {
                return mc.player.getEyePos();
            } catch (Throwable ignored) {
                return new Vec3d(0, 0, 0);
            }
        }
    }

    public static int[] project(Vec3d p, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.getWindow() == null) return null;
        try {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            Vec3d target = mc.player.getEyePos();
            try {
                Freecam freecam = ModuleManager.getModule(Freecam.class);
                if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                    yaw = freecam.getCamYaw();
                    pitch = freecam.getCamPitch();
                    target = new Vec3d(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
                } else {
                    FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
                    if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive()) {
                        yaw = freeLook.getLookYaw();
                        pitch = freeLook.getLookPitch();
                    }
                }
            } catch (Throwable ignored) {}

            // Interpolate the eye to render time by tracked displacement
            // (exact, unlike velocity which breaks under acceleration).
            if (!eyeInit || Math.abs(target.x - lastEyeX) > 64 || Math.abs(target.y - lastEyeY) > 64
                    || Math.abs(target.z - lastEyeZ) > 64) {
                prevEyeX = lastEyeX = target.x;
                prevEyeY = lastEyeY = target.y;
                prevEyeZ = lastEyeZ = target.z;
                eyeInit = true;
            }
            double tt = Math.max(0.0, Math.min(1.0, tickDelta));
            Vec3d origin = new Vec3d(
                    prevEyeX + (target.x - prevEyeX) * tt,
                    prevEyeY + (target.y - prevEyeY) * tt,
                    prevEyeZ + (target.z - prevEyeZ) * tt);
            prevEyeX = lastEyeX = target.x;
            prevEyeY = lastEyeY = target.y;
            prevEyeZ = lastEyeZ = target.z;

            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            double cosP = Math.cos(pitchRad);
            double fx = -Math.sin(yawRad) * cosP;
            double fy = -Math.sin(pitchRad);
            double fz = Math.cos(yawRad) * cosP;
            double dx = p.x - origin.x, dy = p.y - origin.y, dz = p.z - origin.z;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.1) return null;
            double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            double cx = dx * rx + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            float effFov = 70.0f;
            try {
                effFov = ((GameRendererMixin) (Object) mc.gameRenderer).callGetFov(
                        mc.gameRenderer.getCamera(), tickDelta, true);
            } catch (Throwable ignored) {
                try {
                    effFov = mc.options.getFov().getValue();
                } catch (Throwable ignored2) {}
            }
            double f = (h / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, effFov)) / 2.0));
            return new int[]{(int) (w / 2.0 + cx / depth * f), (int) (h / 2.0 - cy / depth * f)};
        } catch (Throwable t) {
            return null;
        }
    }
}
