package com.anormal.client.util;

import com.anormal.client.mixin.GameRendererMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Single world -> screen projector for every marker module (47 callers).
 *
 * <p><b>Why markers used to jitter / "slide" while walking, running or
 * flying:</b> the old version read {@code mc.player.getYaw()/getPitch()},
 * which are the <i>tick-quantised</i> player values (updated 20x per second),
 * and reconstructed the eye height itself from {@code getEyePos().y - getY()}
 * (the raw, current-tick value). Minecraft renders with <b>interpolated</b>
 * values, so every marker was drawn against a camera that did not match the
 * one the world was rendered with - the faster you moved, the further the
 * markers slid away from their blocks.</p>
 *
 * <p>The fix is to use the real render camera. {@link Camera} is updated once
 * per render frame by {@code Camera.update()} and holds the interpolated
 * {@link Camera#getYaw()}, {@link Camera#getPitch()} and
 * {@link Camera#getCameraPos()}. Those are exactly the values the world is
 * drawn with, so markers stay pinned to their blocks at any speed. Using the
 * camera also means Freecam / FreeLook are handled automatically, because
 * {@code CameraMixin} writes their transform straight into this object.</p>
 *
 * <p>All of it is cached once per frame by {@link #beginFrame(float)}, so the
 * (relatively expensive) FOV lookup happens once instead of once per projected
 * point.</p>
 */
public final class ProjectionUtil {
    private ProjectionUtil() {}

    // ---- Per-frame camera cache -------------------------------------------
    private static boolean frameValid;
    private static float camYaw, camPitch;
    private static double camX, camY, camZ;
    private static double focal;
    private static int screenW, screenH;

    /**
     * Refreshes the camera cache. Called once per HUD frame by
     * {@code ModuleManager.onRender2D}. Safe to call at any time.
     */
    public static void beginFrame(float tickDelta) {
        frameValid = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.getWindow() == null) return;
        try {
            Camera cam = mc.gameRenderer.getCamera();
            if (cam == null || !cam.isReady()) return;

            camYaw = cam.getYaw();
            camPitch = cam.getPitch();
            Vec3d pos = cam.getCameraPos();
            camX = pos.x;
            camY = pos.y;
            camZ = pos.z;

            screenW = mc.getWindow().getScaledWidth();
            screenH = mc.getWindow().getScaledHeight();

            float fov;
            try {
                fov = ((GameRendererMixin) (Object) mc.gameRenderer).callGetFov(cam, tickDelta, true);
            } catch (Throwable t) {
                try {
                    fov = mc.options.getFov().getValue();
                } catch (Throwable t2) {
                    fov = 70.0f;
                }
            }
            fov = Math.max(30.0f, Math.min(110.0f, fov));
            focal = (screenH / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);

            frameValid = true;
        } catch (Throwable ignored) {
        }
    }

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

    /** Interpolated camera (eye) position for the current render frame. */
    public static Vec3d eye(float tickDelta) {
        if (!frameValid) beginFrame(tickDelta);
        if (frameValid) return new Vec3d(camX, camY, camZ);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Vec3d.ZERO;
        try {
            return mc.player.getLerpedPos(tickDelta).add(0, mc.player.getStandingEyeHeight(), 0);
        } catch (Throwable ignored) {
            return Vec3d.ZERO;
        }
    }

    /**
     * Projects a world point to screen space.
     *
     * @return {@code {x, y}} in scaled screen coordinates, or {@code null} if
     *         the point is behind the camera.
     */
    public static int[] project(Vec3d p, float tickDelta) {
        if (p == null) return null;
        if (!frameValid) beginFrame(tickDelta);
        if (!frameValid) return null;

        try {
            double yawRad = Math.toRadians(camYaw);
            double pitchRad = Math.toRadians(camPitch);
            double cosP = Math.cos(pitchRad);

            // Forward vector (same convention as Vec3d.fromPolar).
            double fx = -Math.sin(yawRad) * cosP;
            double fy = -Math.sin(pitchRad);
            double fz = Math.cos(yawRad) * cosP;

            double dx = p.x - camX, dy = p.y - camY, dz = p.z - camZ;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.05) return null;

            // Right vector (ry = 0 for a yaw-only basis).
            double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);

            // Up = right x forward.
            double ux = -rz * fy;
            double uy = rz * fx - rx * fz;
            double uz = rx * fy;

            double cx = dx * rx + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;

            return new int[]{
                    (int) (screenW / 2.0 + cx / depth * focal),
                    (int) (screenH / 2.0 - cy / depth * focal)
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /** Screen-space distance from the crosshair, used for marker sorting. */
    public static double screenDistance(int[] screenPoint) {
        if (screenPoint == null) return Double.MAX_VALUE;
        double dx = screenPoint[0] - screenW / 2.0;
        double dy = screenPoint[1] - screenH / 2.0;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Camera-space offset of a world point.
     *
     * @return {@code {right, up, forward}} (forward > 0 means in front of the
     *         camera), or {@code null} when the camera is unavailable.
     */
    public static double[] cameraSpace(Vec3d p, float tickDelta) {
        if (p == null) return null;
        if (!frameValid) beginFrame(tickDelta);
        if (!frameValid) return null;
        try {
            double yawRad = Math.toRadians(camYaw);
            double pitchRad = Math.toRadians(camPitch);
            double cosP = Math.cos(pitchRad);

            double fx = -Math.sin(yawRad) * cosP;
            double fy = -Math.sin(pitchRad);
            double fz = Math.cos(yawRad) * cosP;
            double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
            double ux = -rz * fy;
            double uy = rz * fx - rx * fz;
            double uz = rx * fy;

            double dx = p.x - camX, dy = p.y - camY, dz = p.z - camZ;
            return new double[]{
                    dx * rx + dz * rz,
                    dx * ux + dy * uy + dz * uz,
                    dx * fx + dy * fy + dz * fz
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /** Interpolated camera yaw, for modules that need the render-frame value. */
    public static float cameraYaw(float tickDelta) {
        if (!frameValid) beginFrame(tickDelta);
        return camYaw;
    }

    public static float cameraPitch(float tickDelta) {
        if (!frameValid) beginFrame(tickDelta);
        return camPitch;
    }
}
