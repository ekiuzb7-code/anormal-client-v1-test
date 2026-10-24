package com.lucidclient.module.setting;

import com.lucidclient.ui.util.MathUtils;

/**
 * An integer setting rendered as a slider.
 */
public class IntSetting extends Setting<Integer> {
    private final int min;
    private final int max;
    private final int step;

    public IntSetting(String name, String description, int min, int max, int defaultValue, int step) {
        super(name, description, MathUtils.clamp(defaultValue, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }
    public int getStep() { return step; }

    public void setInt(int v) {
        this.value = MathUtils.clamp(v, min, max);
    }

    public float getRatio() {
        if (max <= min) return 0f;
        return (float) (value - min) / (max - min);
    }

    public void setRatio(float r) {
        r = MathUtils.clamp(r, 0f, 1f);
        int v = Math.round(min + r * (max - min));
        // Snap to step
        v = Math.round((float) v / step) * step;
        this.value = MathUtils.clamp(v, min, max);
    }

    @Override public int getControlHeight() { return 28; }
    @Override public String getType() { return "int"; }
}
