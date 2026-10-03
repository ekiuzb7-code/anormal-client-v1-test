package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.math.MathHelper;

public class Speed extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Speed mode", "Normal", "Normal", "BHop", "Timer", "Strafe");
    public final NumberSetting speed = new NumberSetting("Speed", "Speed multiplier", 1.5, 0.5, 5.0, 0.1);
    public final BooleanSetting jump = new BooleanSetting("Auto Jump", "Auto jump when stuck", false);

    public Speed() {
        super("Speed", "Increases movement speed", Category.UZNY11);
        addSetting(mode);
        addSetting(speed);
        addSetting(jump);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.isOnGround() == false) return;

        double s = speed.getValue();

        if (mode.is("Normal")) {
            if (mc.options.forwardKey.isPressed()) {
                float yaw = mc.player.getYaw();
                double moveX = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * (s * 0.1);
                double moveZ = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * (s * 0.1);
                mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
            }
        } else if (mode.is("BHop")) {
            if (mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
                mc.player.jump();
                float yaw = mc.player.getYaw();
                double moveX = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * (s * 0.2);
                double moveZ = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * (s * 0.2);
                mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
            }
        } else if (mode.is("Strafe")) {
            if (mc.options.forwardKey.isPressed()) {
                float yaw = mc.player.getYaw();
                double moveX = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * (s * 0.15);
                double moveZ = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * (s * 0.15);
                mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
            }
        } else if (mode.is("Timer")) {
            // Timer hack - would need mixin for game timer
        }
    }
}