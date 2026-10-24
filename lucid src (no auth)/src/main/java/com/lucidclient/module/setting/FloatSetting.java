package com.lucidclient.module.setting;

import com.lucidclient.ui.util.MathUtils;

/**
 * A floating-point setting rendered as a slider with min/max bounds.
 */
public class FloatSetting extends Setting<Float> {
    private final float min;
    private final float max;
    private final int decimals;

    public FloatSetting(String name, String description, float min, float max, float defaultValue, int decimals) {
        super(name, description, MathUtils.clamp(defaultValue, min, max));
        this.min = min;
        this.max = max;
        this.decimals = decimals;
    }

    public float getMin() { return min; }
    public float getMax() { return max; }
    public int getDecimals() { return decimals; }

    /** Set value, clamped to [min, max]. */
    public void setFloat(float v) {
        this.value = MathUtils.clamp(v, min, max);
    }

    /** Normalized 0..1 position of current value within range. */
    public float getRatio() {
        if (max <= min) return 0f;
        return (value - min) / (max - min);
    }

    /** Set value from normalized 0..1 ratio. */
    public void setRatio(float r) {
        r = MathUtils.clamp(r, 0f, 1f);
        this.value = min + r * (max - min);
    }

    @Override public int getControlHeight() { return 28; }
    @Override public String getType() { return "float"; }
}
