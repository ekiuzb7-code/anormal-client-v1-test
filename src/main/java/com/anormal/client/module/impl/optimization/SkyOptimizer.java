package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;

/**
 * Simplifies the sky.
 *
 * <p>The sky dome, the star field, the sun/moon and the cloud layer are all
 * drawn every frame even though you rarely look at them. {@code WorldRendererMixin}
 * lets this module cancel each part individually.</p>
 */
public class SkyOptimizer extends Optimizer {

    public final BooleanSetting hideSky = new BooleanSetting("Hide Sky", "Skip the sky dome entirely", true);
    public final BooleanSetting hideStars = new BooleanSetting("Hide Stars", "Skip the star field", true);
    public final BooleanSetting hideSunMoon = new BooleanSetting("Hide Sun & Moon", "Skip the sun and moon", false);
    public final BooleanSetting hideVoid = new BooleanSetting("Hide Void", "Hide the dark void plane", false);
    public final BooleanSetting hideClouds = new BooleanSetting("Hide Clouds", "Skip the cloud layer", true);
    public final BooleanSetting hideWeather = new BooleanSetting("Hide Weather", "Skip rain and snow rendering", true);
    public final BooleanSetting hideHorizon = new BooleanSetting("Hide Horizon Fog", "Skip the horizon band", false);

    public final ModeSetting skyColorMode = new ModeSetting("Sky Color", "Override the sky colour", "Vanilla",
            "Vanilla", "Custom", "Black", "Biome");
    public final ColorSetting customSky = new ColorSetting("Custom Color", "Colour used by the Custom mode", ColorUtils.rgba(20, 24, 40, 255));
    public final NumberSetting brightness = new NumberSetting("Brightness", "Sky brightness multiplier", 1.0, 0.0, 2.0, 0.05);
    public final BooleanSetting staticTime = new BooleanSetting("Static Time", "Freeze the sky at one time of day", false);
    public final NumberSetting timeOfDay = new NumberSetting("Time Of Day", "0 = sunrise, 6000 = noon, 18000 = midnight", 6000.0, 0.0, 24000.0, 500.0);

    public SkyOptimizer() {
        super("SkyOptimizer", "Simplifies the sky, stars, sun, moon and clouds");
        addSetting(hideSky);
        addSetting(hideStars);
        addSetting(hideSunMoon);
        addSetting(hideVoid);
        addSetting(hideClouds);
        addSetting(hideWeather);
        addSetting(hideHorizon);
        addSetting(skyColorMode);
        addSetting(customSky);
        addSetting(brightness);
        addSetting(staticTime);
        addSetting(timeOfDay);
    }

    @Override
    public void onTick() {
        if (!staticTime.isEnabled() || mc.world == null) return;
        try {
            mc.world.setTime(mc.world.getTime(), timeOfDay.getValue().longValue(), false);
        } catch (Throwable ignored) {
        }
    }

    // ---- Static queries used by the mixins -------------------------------

    private static SkyOptimizer get() {
        SkyOptimizer m = com.anormal.client.module.ModuleManager.getModule(SkyOptimizer.class);
        return (m != null && m.isEnabled()) ? m : null;
    }

    public static boolean hideSkyDome() {
        SkyOptimizer m = get();
        return m != null && m.hideSky.isEnabled();
    }

    public static boolean hideCloudLayer() {
        SkyOptimizer m = get();
        return m != null && m.hideClouds.isEnabled();
    }

    public static boolean hideWeatherLayer() {
        SkyOptimizer m = get();
        return m != null && m.hideWeather.isEnabled();
    }

    public static int skyColorOverride(int vanilla) {
        SkyOptimizer m = get();
        if (m == null || m.skyColorMode.is("Vanilla") || m.skyColorMode.is("Biome")) return vanilla;
        if (m.skyColorMode.is("Black")) return 0xFF000000;
        return m.customSky.getValue();
    }
}
