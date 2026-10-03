package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.BooleanSetting;

public class HitFlick extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Hit flick mode", "Normal", "Normal", "Predict", "Lag");
    public final NumberSetting angle = new NumberSetting("Angle", "Flick angle", 90.0, 0.0, 360.0, 1.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Flick chance %", 100.0, 0.0, 100.0, 1.0);

    public HitFlick() {
        super("HitFlick", "Flicks view angle on hit", Category.UZNY11);
        addSetting(mode);
        addSetting(angle);
        addSetting(chance);
    }

    @Override
    public void onTick() {
        // HitFlick logic
    }
}