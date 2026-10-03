package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.math.MathHelper;

public class Strafe extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Strafe mode", "Normal", "Normal", "Directional", "Circles");
    public final NumberSetting speed = new NumberSetting("Speed", "Strafe speed", 1.0, 0.1, 3.0, 0.1);

    public Strafe() {
        super("Strafe", "Automatically strafes for better movement", Category.UZNY11);
        addSetting(mode);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.isOnGround() == false) return;

        if (mode.is("Directional")) {
            if (mc.options.leftKey.isPressed() && mc.options.rightKey.isPressed()) return;
            float yaw = mc.player.getYaw();
            double moveX = -MathHelper.sin(mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE) * speed.getValue() * 0.1;
            double moveZ = MathHelper.cos(mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE) * speed.getValue() * 0.1;
            if (mc.options.leftKey.isPressed()) {
                mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
            } else if (mc.options.rightKey.isPressed()) {
                mc.player.setVelocity(-moveX, mc.player.getVelocity().y, -moveZ);
            }
        } else if (mode.is("Circles")) {
            // Circle strafe around target
        }
    }
}