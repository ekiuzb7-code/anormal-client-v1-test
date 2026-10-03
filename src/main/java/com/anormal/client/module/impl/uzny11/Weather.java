package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;

public class Weather extends Module {
    public final ModeSetting weather = new ModeSetting("Weather", "Client weather override", "Clear", "Clear", "Rain", "Thunder");

    public Weather() {
        super("Weather", "Overrides client-side rendered weather condition", Category.UZNY11);
        addSetting(weather);
    }

    @Override
    public void onTick() {
        if (mc.world != null) {
            if (weather.is("Clear")) {
                mc.world.getLevelProperties().setRaining(false);
            } else {
                mc.world.getLevelProperties().setRaining(true);
            }
        }
    }
}
