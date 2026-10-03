package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class Timer extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Game speed multiplier", 1.0, 0.1, 3.0, 0.1);

    public Timer() {
        super("Timer", "Changes game speed", Category.UZNY11);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        // Timer hack - would need mixin for game timer
    }
}