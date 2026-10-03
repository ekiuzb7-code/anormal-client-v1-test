package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Fly extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Fly horizontal speed", 0.5, 0.1, 5.0, 0.1);
    public final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", "Fly vertical speed", 0.2, 0.1, 5.0, 0.1);

    public Fly() {
        super("Fly", "Creative-like flight", Category.UZNY11);
        addSetting(speed); addSetting(verticalSpeed);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;
        try {
            double yaw = Math.toRadians(mc.player.getYaw());
            double f = speed.getValue() * 0.14;
            double mx = 0, mz = 0;
            if (mc.options.forwardKey.isPressed()) { mx += -Math.sin(yaw) * f; mz += Math.cos(yaw) * f; }
            if (mc.options.backKey.isPressed()) { mx -= -Math.sin(yaw) * f; mz -= Math.cos(yaw) * f; }
            if (mc.options.leftKey.isPressed()) { mx += Math.cos(yaw) * f; mz += -Math.sin(yaw) * f; }
            if (mc.options.rightKey.isPressed()) { mx -= Math.cos(yaw) * f; mz -= -Math.sin(yaw) * f; }
            double vy = 0;
            if (mc.options.jumpKey.isPressed()) vy = verticalSpeed.getValue() * 0.35;
            else if (mc.options.sneakKey.isPressed()) vy = -verticalSpeed.getValue() * 0.35;
            else vy = 0;
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x * 0.6 + mx, vy, v.z * 0.6 + mz);
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    private boolean wasFlying = false;

    public boolean wasFlying() {
        return wasFlying;
    }

    @Override
    public void onDisable() {
        wasFlying = false;
    }
}
