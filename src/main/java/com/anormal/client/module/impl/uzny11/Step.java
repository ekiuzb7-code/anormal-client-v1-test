package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class Step extends Module {
    public final NumberSetting height = new NumberSetting("Height", "Step height blocks", 1.0, 0.5, 2.5, 0.5);
    public final BooleanSetting onlyWhenSprinting = new BooleanSetting("Only When Sprinting", "Only while sprinting", false);

    public Step() {
        super("Step", "Steps up blocks instantly", Category.UZNY11);
        addSetting(height); addSetting(onlyWhenSprinting);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (onlyWhenSprinting.getValue() && !mc.player.isSprinting()) return;
            if (!mc.player.horizontalCollision || !mc.player.isOnGround()) return;
            if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;
            double h = Math.min(height.getValue(), 2.0);
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + h, mc.player.getZ());
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    private int steps = 0;

    public int getSteps() {
        return steps;
    }

    @Override
    public void onDisable() {
        steps = 0;
    }
}
