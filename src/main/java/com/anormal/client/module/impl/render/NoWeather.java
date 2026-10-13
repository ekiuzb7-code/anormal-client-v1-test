package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Ported from Wurst's NoWeatherHack.
 *
 * <p>Wurst's settings, verbatim:</p>
 * <ul>
 *   <li>"Disable Rain" (default on)</li>
 *   <li>"Change World Time" (default off) + "Time" (0..23900, step 100)</li>
 *   <li>"Change Moon Phase" (default off) + "Moon Phase" (0..7)</li>
 * </ul>
 *
 * <p>Rain and time are applied through {@code WorldMixin}, exactly like Wurst
 * does. The moon-phase option is present for parity but is a no-op on this
 * Minecraft version: 1.21.11 no longer exposes a moon-phase getter on
 * {@code World}, so there is nothing client-side to override.</p>
 */
public class NoWeather extends Module {
    public final BooleanSetting disableRain = new BooleanSetting("Disable Rain", "Turns rain and snow off", true);
    public final BooleanSetting changeTime = new BooleanSetting("Change World Time", "Forces the world time to a fixed value", false);
    public final NumberSetting time = new NumberSetting("Time", "The world time to force (0 = sunrise, 6000 = noon, 13000 = sunset, 18000 = midnight)", 6000.0, 0.0, 23900.0, 100.0);
    public final BooleanSetting changeMoonPhase = new BooleanSetting("Change Moon Phase", "Forces the moon phase (no-op on 1.21.11)", false);
    public final NumberSetting moonPhase = new NumberSetting("Moon Phase", "The moon phase to force (0..7)", 0.0, 0.0, 7.0, 1.0);

    public NoWeather() {
        super("NoWeather", "Removes rain and can force the world time", Category.RENDER);
        addSetting(disableRain);
        addSetting(changeTime);
        addSetting(time);
        addSetting(changeMoonPhase);
        addSetting(moonPhase);

        time.visibleIf(changeTime::isEnabled);
        moonPhase.visibleIf(changeMoonPhase::isEnabled);
    }

    /** Wurst: {@code isRainDisabled()}. */
    public boolean isRainDisabled() {
        return isEnabled() && disableRain.isEnabled();
    }

    /** Wurst: {@code isTimeChanged()}. */
    public boolean isTimeChanged() {
        return isEnabled() && changeTime.isEnabled();
    }

    /** Wurst: {@code getChangedTime()}. */
    public long getChangedTime() {
        return time.getValue().longValue();
    }
}
