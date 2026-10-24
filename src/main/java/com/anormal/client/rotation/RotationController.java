package com.anormal.client.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shared rotation driver for the combat modules.
 *
 * <p>One instance per module: the rotator keeps internal state (WindMouse especially), so sharing
 * an instance between modules would make their paths fight each other.</p>
 *
 * <p><b>Silent accumulation fix.</b> The first version re-anchored to the camera on every tick.
 * In visible mode that is correct - the camera <em>is</em> the output. In silent mode the camera
 * never moves, so the rotator restarted from the same angle every tick and the smoothed rotation
 * never converged: it stayed at a fixed fraction of the remaining angle. The controller now keeps
 * its own current rotation and only follows the camera when the player actually turns.</p>
 */
public final class RotationController {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** How far the camera may drift before we assume the player turned it themselves. */
    private static final float CAMERA_DRIFT = 35.0f;

    private Rotator rotator;
    private Smoothing active;
    private Rotation internal;
    private Rotation lastSent;
    private Rotation lastWanted;
    private float elapsed;

    // Last silent rotation shared with the renderer, so the player model can turn its head
    // the same way the server thinks it is turned.
    private static Rotation lastSilent;
    private static long lastSilentAt;

    /** Advances the rotation one tick towards {@code aimPoint} (visible mode). */
    public Rotation step(Vec3d aimPoint, float smoothness, Smoothing smoothing, boolean verticalAim) {
        return step(aimPoint, smoothness, smoothing, verticalAim, false);
    }

    /**
     * Advances the rotation one tick towards {@code aimPoint}.
     *
     * @param silent {@code true} keeps the camera untouched, so the controller accumulates its own
     *               rotation instead of restarting from the camera every tick
     */
    public Rotation step(Vec3d aimPoint, float smoothness, Smoothing smoothing,
                         boolean verticalAim, boolean silent) {
        Smoothing chosen = smoothing == null ? Smoothing.EASE_OUT_CUBIC : smoothing;
        if (rotator == null || active != chosen) {
            rotator = chosen.create();
            active = chosen;
        }

        Rotation camera = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        Rotation anchor;
        Rotation wanted = Rotation.toward(mc.player.getEyePos(), aimPoint);
        if (!verticalAim) wanted = new Rotation(wanted.yaw(), camera.pitch());

        // A new target (or a target that jumped) restarts the easing curve. Without this the
        // easing fraction keeps growing while the aim point keeps moving, which is what made the
        // aim slowly circle around the target instead of locking on.
        if (lastWanted == null || lastWanted.distanceTo(wanted) > 60.0f) elapsed = 0.0f;
        else elapsed += 1.0f;
        lastWanted = wanted;

        if (!silent) {
            anchor = camera;
            internal = camera;
        } else {
            if (internal == null) {
                internal = camera;
            } else if (Math.abs(MathHelper.wrapDegrees(internal.yaw() - camera.yaw())) > CAMERA_DRIFT
                    || Math.abs(internal.pitch() - camera.pitch()) > CAMERA_DRIFT) {
                // The player turned their head by hand - follow them instead of fighting.
                internal = camera;
            }
            anchor = internal;
        }

        if (!verticalAim) wanted = new Rotation(wanted.yaw(), anchor.pitch());

        RotationConfig config = RotationConfig.visible(
                MathHelper.clamp(smoothness, 0.0f, 1.0f), chosen);

        // EaseOutCubic expects the elapsed time since the rotation started, the other two are
        // per-tick integrators and want a constant 1.0.
        float delta = chosen == Smoothing.EASE_OUT_CUBIC ? elapsed : 1.0f;
        Rotation next = rotator.step(anchor, wanted, config, delta);
        internal = next;
        return next;
    }

    /**
     * Applies a rotation.
     *
     * @param silent {@code true} keeps the camera untouched and only tells the server
     */
    public void apply(Rotation rotation, boolean silent, boolean gcdFix) {
        float yaw = rotation.yaw();
        float pitch = rotation.pitch();

        if (gcdFix) {
            float gcd = gcdStep();
            if (gcd > 1.0E-5f) {
                float deltaYaw = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
                float deltaPitch = pitch - mc.player.getPitch();
                yaw = mc.player.getYaw() + Math.round(deltaYaw / gcd) * gcd;
                pitch = MathHelper.clamp(mc.player.getPitch() + Math.round(deltaPitch / gcd) * gcd, -90.0f, 90.0f);
            }
        }

        if (!silent) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            lastSilent = null;
            return;
        }
        sendIfChanged(yaw, pitch);
    }

    /** Sends the rotation without the change detection - used right before an attack. */
    public void send(float yaw, float pitch) {
        try {
            var handler = mc.getNetworkHandler();
            if (handler == null) return;
            handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            lastSent = new Rotation(yaw, pitch);
            lastSilent = lastSent;
            lastSilentAt = System.currentTimeMillis();
            // Keep the internal state in sync so the next smoothed step starts from what the
            // server already knows.
            internal = new Rotation(yaw, pitch);
        } catch (Throwable ignored) {
        }
    }

    /** Sends only when the value actually changed, so the queue is not spammed every tick. */
    private void sendIfChanged(float yaw, float pitch) {
        if (lastSent != null
                && Math.abs(lastSent.yaw() - yaw) < 0.01f
                && Math.abs(lastSent.pitch() - pitch) < 0.01f) {
            return;
        }
        send(yaw, pitch);
    }

    /** True when a silent rotation is currently being sent to the server. */
    public boolean hasRotation() {
        return lastSent != null;
    }

    /**
     * The rotation the server currently believes the local player has, or {@code null} when there
     * is no fresh silent rotation. Used by the renderer so the player model turns its head the
     * same way the server thinks it is turned.
     */
    public static Rotation serverRotation() {
        if (lastSilent == null) return null;
        if (System.currentTimeMillis() - lastSilentAt > 400L) return null;
        return lastSilent;
    }

    public void reset() {
        rotator = null;
        active = null;
        internal = null;
        lastSent = null;
        lastWanted = null;
        elapsed = 0.0f;
    }

    /**
     * Smallest rotation step the player's mouse sensitivity can produce. Rounding to it makes the
     * movement indistinguishable from real mouse input.
     */
    public static float gcdStep() {
        try {
            double sensitivity = mc.options.getMouseSensitivity().getValue();
            double f = sensitivity * 0.6 + 0.2;
            return (float) (f * f * f * 8.0 * 0.15);
        } catch (Throwable t) {
            return 0.0f;
        }
    }
}
