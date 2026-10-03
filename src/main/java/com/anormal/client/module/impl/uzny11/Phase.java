package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

public class Phase extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Phase direction", "Forward", "Forward", "Up");
    public final NumberSetting clipDistance = new NumberSetting("Clip Distance", "Clip distance blocks", 3.0, 1.0, 6.0, 0.5);

    public Phase() {
        super("Phase", "Phase through walls", Category.UZNY11);
        addSetting(mode); addSetting(clipDistance);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (!mc.player.horizontalCollision || !mc.player.isSneaking()) return;
            double rad = Math.toRadians(mc.player.getYaw());
            double d = Math.min(clipDistance.getValue(), 4.0);
            if (mode.is("Up")) {
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + d, mc.player.getZ());
            } else {
                mc.player.setPosition(mc.player.getX() - Math.sin(rad) * d, mc.player.getY(), mc.player.getZ() + Math.cos(rad) * d);
            }
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    private int phases = 0;

    public int getPhases() {
        return phases;
    }

    @Override
    public void onDisable() {
        phases = 0;
    }
}
