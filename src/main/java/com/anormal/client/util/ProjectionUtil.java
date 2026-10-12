package com.anormal.client.util;

import com.anormal.client.mixin.GameRendererMixin;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Single world -> screen projector for every marker module.
 *
 * <p><b>Jitter fix:</b> the old version tracked the eye position itself with
 * {@code prevEye/lastEye} fields and rewrote them on every call. Because the HUD
 * renderer calls {@code project()} once per entity per frame, only the *first*
 * entity of each frame got an interpolated origin - every other entity used the
 * raw, tick-quantised position and visibly jittered. The origin is now taken
 * from {@link Entity#getLerpedPos(float)}, which Minecraft maintains per render
 * frame, so all entities are smooth.</p>
 */
public final class ProjectionUtil {
    private ProjectionUtil() {}

    /** Smooth world position of an entity for the current render frame. */
    public static Vec3d lerpPos(Entity entity, float tickDelta) {
        if (entity == null) return Vec3d.ZERO;
        try {
            return entity.getLerpedPos(tickDelta);
        } catch (Throwable t) {
            try {
                return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            } catch (Throwable ignored) {
                return Vec3d.ZERO;
            }
        }
    }

    /**
     * Interpolated world position of an entity centre for render time t.
     * Kept for callers that only have raw coordinates.
     */
    private static final java.util.Map<Integer, double[]> PREV = new java.util.HashMap<>();

    public static double[] lerpEntity(int id, double x, double y, double z, float t) {
        double[] prev = PREV.get(id);
        if (prev == null || Math.abs(prev[0] - x) > 32 || Math.abs(prev[1] - y) > 32 || Math.abs(prev[2] - z) > 32) {
            prev = new double[]{x, y, z};
            PREV.put(id, prev);
            if (PREV.size() > 500) PREV.clear();
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

    /** Interpolated eye position for the current render frame. */
    public static Vec3d eye(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Vec3d.ZERO;
        try {
            Vec3d base = mc.player.getLerpedPos(tickDelta);
            double eyeOffset = mc.player.getEyePos().y - mc.player.getY();
            return base.add(0.0, eyeOffset, 0.0);
        } catch (Throwable t) {
            try {
                return mc.player.getEyePos();
            } catch (Throwable ignored) {
                return Vec3d.ZERO;
            }
        }
    }

    public static int[] project(Vec3d p, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.getWindow() == null) return null;
        try {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            Vec3d origin = eye(tickDelta);

            try {
                Freecam freecam = ModuleManager.getModule(Freecam.class);
                if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                    yaw = freecam.getCamYaw();
                    pitch = freecam.getCamPitch();
                    origin = new Vec3d(
                            freecam.getInterpolatedX(tickDelta),
                            freecam.getInterpolatedY(tickDelta),
                            freecam.getInterpolatedZ(tickDelta));
                } else {
                    FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
                    if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive()) {
                        yaw = freeLook.getLookYaw();
                        pitch = freeLook.getLookPitch();
                    }
                }
            } catch (Throwable ignored) {}

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
