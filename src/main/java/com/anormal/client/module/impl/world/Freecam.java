package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Detached fly camera. The player body stays frozen in place while the camera
 * moves freely through the world (including through walls and underground).
 *
 * <p>Mouse look is intercepted by {@code EntityMixin} and routed to
 * {@link #addLook(double, double)}, so the camera rotation is updated per frame
 * and the body never turns. The actual camera transform is applied by
 * {@code CameraMixin} using the interpolated position.</p>
 */
public class Freecam extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Flight camera speed", 1.5, 0.5, 5.0, 0.5);
    public final BooleanSetting allowInteracting = new BooleanSetting("Allow Interacting", "Interact with blocks and entities", false);
    public final BooleanSetting showPlayer = new BooleanSetting("Show Player", "Mark your real body position", true);
    public final ModeSetting style = new ModeSetting("Style", "V1 or V2 implementation", "V1", "V1", "V2");
    public final NumberSetting v2Speed = new NumberSetting("V2 Speed", "V2 flight camera speed", 1.0, 0.1, 2.0, 0.1);
    public final BooleanSetting v2Interact = new BooleanSetting("V2 Interact", "Interact from camera perspective", true);
    public final BooleanSetting v2CancelDamage = new BooleanSetting("V2 Cancel Damage", "Disable on damage", true);
    public final BooleanSetting v2CancelTeleport = new BooleanSetting("V2 Cancel Teleport", "Disable on teleport", true);

    // Frozen real body
    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorPitch;

    // Free camera
    private double camX, camY, camZ;
    private double prevCamX, prevCamY, prevCamZ;
    private float camYaw, camPitch;

    private boolean active = false;
    private float lastHealth = 20.0f;

    public Freecam() {
        super("Freecam", "Detached camera fly (body stays frozen, server-safe look)", Category.WORLD);
        addSetting(speed);
        addSetting(allowInteracting);
        addSetting(showPlayer);
        addSetting(style);
        addSetting(v2Speed);
        addSetting(v2Interact);
        addSetting(v2CancelDamage);
        addSetting(v2CancelTeleport);
        v2Speed.visibleIf(() -> style.is("V2"));
        v2Interact.visibleIf(() -> style.is("V2"));
        v2CancelDamage.visibleIf(() -> style.is("V2"));
        v2CancelTeleport.visibleIf(() -> style.is("V2"));
    }

    @Override
    public void onEnable() {
        active = false;
        if (mc.player == null) return;

        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        anchorYaw = mc.player.getYaw();
        anchorPitch = mc.player.getPitch();

        Vec3d eye = mc.player.getEyePos();
        camX = prevCamX = eye.x;
        camY = prevCamY = eye.y;
        camZ = prevCamZ = eye.z;
        camYaw = anchorYaw;
        camPitch = anchorPitch;

        try {
            lastHealth = mc.player.getHealth();
        } catch (Throwable ignored) {
            lastHealth = 20.0f;
        }
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
        if (mc.player == null) return;
        try {
            mc.player.setPos(anchorX, anchorY, anchorZ);
            mc.player.setVelocity(0.0, 0.0, 0.0);
            mc.player.fallDistance = 0.0f;
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        } catch (Throwable ignored) {
        }
    }

    /** Called per frame by EntityMixin. Deltas are already scaled like vanilla. */
    public void addLook(double deltaYaw, double deltaPitch) {
        camYaw = MathHelper.wrapDegrees(camYaw + (float) deltaYaw);
        camPitch = MathHelper.clamp(camPitch + (float) deltaPitch, -90.0f, 90.0f);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !active) return;
        boolean v2 = style.is("V2");

        // V2 cancel triggers: damage or teleport ends the camera trip.
        if (v2) {
            try {
                float hp = mc.player.getHealth();
                if (v2CancelDamage.isEnabled() && hp < lastHealth) {
                    setEnabled(false);
                    return;
                }
                lastHealth = hp;
            } catch (Throwable ignored) {
            }
            if (v2CancelTeleport.isEnabled()) {
                try {
                    double dx = mc.player.getX() - anchorX;
                    double dy = mc.player.getY() - anchorY;
                    double dz = mc.player.getZ() - anchorZ;
                    if (dx * dx + dy * dy + dz * dz > 36.0) {
                        setEnabled(false);
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // Freeze the real body on the anchor (no fall, no drift, no correction).
        try {
            mc.player.setPos(anchorX, anchorY, anchorZ);
            mc.player.setVelocity(0.0, 0.0, 0.0);
            mc.player.fallDistance = 0.0f;
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        } catch (Throwable ignored) {
        }

        boolean interact = v2 ? v2Interact.isEnabled() : allowInteracting.isEnabled();
        if (!interact) {
            try {
                mc.options.attackKey.setPressed(false);
                mc.options.useKey.setPressed(false);
            } catch (Throwable ignored) {
            }
        }

        // Fly the camera (not the player) - no collision, goes anywhere.
        double baseSpeed = v2 ? v2Speed.getValue() : speed.getValue();
        double s = baseSpeed * 0.35;
        double rad = Math.toRadians(camYaw);
        double radP = Math.toRadians(camPitch);
        double fwdX = -Math.sin(rad) * Math.cos(radP);
        double fwdY = -Math.sin(radP);
        double fwdZ = Math.cos(rad) * Math.cos(radP);

        double mx = 0.0, my = 0.0, mz = 0.0;
        if (mc.options.forwardKey.isPressed()) { mx += fwdX; my += fwdY; mz += fwdZ; }
        if (mc.options.backKey.isPressed()) { mx -= fwdX; my -= fwdY; mz -= fwdZ; }
        if (mc.options.leftKey.isPressed()) { mx += fwdZ; mz -= fwdX; }
        if (mc.options.rightKey.isPressed()) { mx -= fwdZ; mz += fwdX; }
        if (mc.options.jumpKey.isPressed()) my += 1.0;
        if (mc.options.sneakKey.isPressed()) my -= 1.0;

        prevCamX = camX;
        prevCamY = camY;
        prevCamZ = camZ;
        camX += mx * s;
        camY += my * s;
        camZ += mz * s;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        // Character outline on the frozen body so you always see where YOU are.
        if (!showPlayer.isEnabled() || mc.player == null || !active) return;
        int[] feet = com.anormal.client.util.ProjectionUtil.project(new Vec3d(anchorX, anchorY, anchorZ), tickDelta);
        int[] head = com.anormal.client.util.ProjectionUtil.project(new Vec3d(anchorX, anchorY + 1.8, anchorZ), tickDelta);
        if (feet == null || head == null) return;
        int top = Math.min(feet[1], head[1]);
        int bottom = Math.max(feet[1], head[1]);
        int half = Math.max(6, (bottom - top) / 5);
        int cx = (feet[0] + head[0]) / 2;
        int col = 0xFF55FF55;
        context.fill(cx - half, top, cx + half, top + 1, col);
        context.fill(cx - half, bottom - 1, cx + half, bottom, col);
        context.fill(cx - half, top, cx - half + 1, bottom, col);
        context.fill(cx + half - 1, top, cx + half, bottom, col);
        if (mc.textRenderer != null) {
            String label = "YOU";
            context.drawText(mc.textRenderer, label, cx - mc.textRenderer.getWidth(label) / 2, top - 11, col, true);
        }
    }

    public boolean isCameraActive() {
        return active && isEnabled() && mc.player != null;
    }

    // Interpolated camera position for smooth rendering between ticks.
    public double getInterpolatedX(float tickDelta) { return prevCamX + (camX - prevCamX) * tickDelta; }
    public double getInterpolatedY(float tickDelta) { return prevCamY + (camY - prevCamY) * tickDelta; }
    public double getInterpolatedZ(float tickDelta) { return prevCamZ + (camZ - prevCamZ) * tickDelta; }

    public double getCamX() { return camX; }
    public double getCamY() { return camY; }
    public double getCamZ() { return camZ; }
    public float getCamYaw() { return camYaw; }
    public float getCamPitch() { return camPitch; }
}
