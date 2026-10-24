package dev.zaza.client.setting;

import java.util.Locale;

public final class SliderSetting extends Setting<Integer> {
    private final int min;
    private final int max;
    private final Measure measure;

    public SliderSetting(String id, int defaultValue, int min, int max) {
        this(id, defaultValue, min, max, Measure.NONE);
    }

    public SliderSetting(String id, int defaultValue, int min, int max, Measure measure) {
        super(id, defaultValue);
        this.min = min;
        this.max = max;
        this.measure = measure;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    @Override
    public void set(Integer value) {
        super.set(Math.clamp(value, min, max));
    }

    @Override
    public String format() {
        return measure.format(value);
    }
}
