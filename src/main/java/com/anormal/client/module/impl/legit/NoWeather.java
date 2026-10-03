package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoWeather extends Module {
    public NoWeather() {
        super("NoWeather", "Forces clear morning sky, rain never appears", Category.LEGIT);
    }

    @Override
    public void onEnable() {
        apply();
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;
        try {
            if (mc.world.getLevelProperties().isRaining()) apply();
        } catch (Throwable ignored) {}
    }

    private void apply() {
        if (mc.world == null) return;
        try {
            mc.world.getLevelProperties().setRaining(false);
            mc.world.getLevelProperties().setTimeOfDay(1000L);
        } catch (Throwable ignored) {}
    }
}
