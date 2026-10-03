package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Speed method", "AntiCheat B", "AntiCheat B", "Bhop");

    public Speed() {
        super("Speed", "Increases movement speed", Category.UZNY11);
        addSetting(mode);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;
            if (mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isGliding()) return;
            double rad = Math.toRadians(mc.player.getYaw());
            double dx = -Math.sin(rad);
            double dz = Math.cos(rad);
            Vec3d v = mc.player.getVelocity();
            if (mode.is("Bhop")) {
                if (mc.player.isOnGround()) mc.player.jump();
                mc.player.setVelocity(v.x + dx * 0.06, v.y, v.z + dz * 0.06);
            } else {
                if (mc.player.isOnGround() && mc.player.isSprinting()) mc.player.setVelocity(v.x + dx * 0.09, v.y, v.z + dz * 0.09);
                else if (!mc.player.isOnGround()) mc.player.setVelocity(v.x + dx * 0.02, v.y, v.z + dz * 0.02);
            }
        } catch (Throwable ignored) {}
    }

    private int ticks = 0;

    public int getTicks() {
        return ticks;
    }

    @Override
    public void onDisable() {
        ticks = 0;
    }
}
