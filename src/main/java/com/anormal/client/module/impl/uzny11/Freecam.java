package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Flight camera speed", 1.5, 0.5, 5.0, 0.5);
    public final BooleanSetting allowInteracting = new BooleanSetting("Allow Interacting", "Interact while in freecam", false);
    public final BooleanSetting spawnFake = new BooleanSetting("Spawn Fake", "Keep snapshot at origin", true);
    public final BooleanSetting moveFake = new BooleanSetting("Move Fake", "Snapshot tracks camera", false);

    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorPitch;
    private double camX, camY, camZ;
    private float camYaw, camPitch;
    private boolean active = false;
    private float lastHealth = 20.0f;

    public Freecam() {
        super("Freecam", "Detached camera fly, body stays frozen", Category.UZNY11);
        addSetting(speed);
        addSetting(allowInteracting);
        addSetting(spawnFake);
        addSetting(moveFake);
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
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
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
            mc.player.setPosition(anchorX, anchorY, anchorZ);
            mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (mc.player == null || !active) return;
        try {
            mc.player.setPosition(anchorX, anchorY, anchorZ);
            mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
        if (!allowInteracting.isEnabled()) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
        try {
            camYaw = mc.player.getYaw();
            camPitch = mc.player.getPitch();
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        } catch (Throwable ignored) {}

        double s = speed.getValue() * 0.35;
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
        camX += mx * s;
        camY += my * s;
        camZ += mz * s;
    }

    public boolean isCameraActive() {
        return active && mc.player != null;
    }

    public double getCamX() { return camX; }
    public double getCamY() { return camY; }
    public double getCamZ() { return camZ; }
    public float getCamYaw() { return camYaw; }
    public float getCamPitch() { return camPitch; }
}
