package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;

public class Fly extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Horizontal fly speed", 1.0, 0.1, 5.0, 0.1);
    public final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", "Up/down fly speed", 1.0, 0.1, 5.0, 0.1);

    public Fly() {
        super("Fly", "Makes you fly (blatant)", Category.UZNY11);
        addSetting(speed);
        addSetting(verticalSpeed);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().flying = true;

        double moveSpeed = speed.getValue();
        double vertSpeed = verticalSpeed.getValue();

        // Handle horizontal movement
        double forward = 0, right = 0;
        if (mc.options.forwardKey.isPressed()) forward = 1;
        if (mc.options.backKey.isPressed()) forward = -1;
        if (mc.options.leftKey.isPressed()) right = -1;
        if (mc.options.rightKey.isPressed()) right = 1;

        if (forward != 0 || right != 0) {
            float yaw = mc.player.getYaw();
            double moveX = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * forward + MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * right;
            double moveZ = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * forward + MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * right;

            double len = Math.sqrt(moveX * moveX + moveZ * moveZ);
            if (len > 0) {
                moveX = moveX / len * moveSpeed;
                moveZ = moveZ / len * moveSpeed;
                mc.player.setVelocity(moveX, mc.player.getVelocity().y, moveZ);
            }
        } else {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }

        // Vertical movement
        double vert = 0;
        if (mc.options.jumpKey.isPressed()) vert = vertSpeed;
        if (mc.options.sneakKey.isPressed()) vert = -vertSpeed;
        mc.player.setVelocity(mc.player.getVelocity().x, vert, mc.player.getVelocity().z);
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
            try {
                mc.player.getAbilities().flySpeed = 0.05f;
            } catch (Exception e) {
                // flySpeed might be private in this mapping
            }
            mc.player.sendAbilitiesUpdate();
        }
    }
}