package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;

public class Step extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Step mode", "Normal", "Normal", "Vanilla", "Packet");
    public final NumberSetting height = new NumberSetting("Height", "Step height", 1.0, 0.5, 2.5, 0.1);

    public Step() {
        super("Step", "Automatically steps up blocks", Category.UZNY11);
        addSetting(mode);
        addSetting(height);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.stepHeight = height.getValue().floatValue();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.stepHeight = 0.6f;
    }
}