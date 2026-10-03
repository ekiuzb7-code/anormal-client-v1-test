package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class TimeChanger extends Module {
    public final NumberSetting time = new NumberSetting("Time", "Client world time in ticks", 6000.0, 0.0, 24000.0, 1000.0);

    public TimeChanger() {
        super("TimeChanger", "Overrides client-side rendered time of day", Category.UZNY11);
        addSetting(time);
    }

    @Override
    public void onTick() {
        if (mc.world != null) {
            mc.world.getLevelProperties().setTimeOfDay(time.getValue().longValue());
        }
    }
}
