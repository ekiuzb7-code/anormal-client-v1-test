package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class OmniSprint extends Module {
    public final NumberSetting minRate = new NumberSetting("Min Rate", "Minimum sprint rate", 1.0, 0.0, 3.0, 0.1);
    public final NumberSetting maxRate = new NumberSetting("Max Rate", "Maximum sprint rate", 1.0, 0.0, 3.0, 0.1);
    public final NumberSetting rateChange = new NumberSetting("Rate Change", "Rate change speed", 1.0, 0.0, 5.0, 0.1);

    public OmniSprint() {
        super("OmniSprint", "Sprints in all directions", Category.UZNY11);
        addSetting(minRate); addSetting(maxRate); addSetting(rateChange);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            double rate = (minRate.getValue() + maxRate.getValue()) / 2.0;
            if (rateChange.getValue() > 0 && maxRate.getValue() > minRate.getValue()) {
                rate = minRate.getValue() + (System.currentTimeMillis() % 2000) / 2000.0 * (maxRate.getValue() - minRate.getValue());
            }
            if (rate <= 0.05) return;
            if ((mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0) && !mc.player.isSneaking()) mc.player.setSprinting(true);
        } catch (Throwable ignored) {}
    }

    private double lastRate = 0;

    public double getLastRate() {
        return lastRate;
    }

    @Override
    public void onDisable() {
        lastRate = 0;
    }
}
