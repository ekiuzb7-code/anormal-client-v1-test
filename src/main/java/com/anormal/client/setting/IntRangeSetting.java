package com.anormal.client.setting;

/**
 * A bounded integer range (min..max), e.g. LiquidBounce's {@code intRange("Delay", 300..600, 0..1000)}.
 * The value is stored as {@code int[]{min, max}}.
 */
public class IntRangeSetting extends Setting<int[]> {
    private final int absMin;
    private final int absMax;

    public IntRangeSetting(String name, String description, int defaultMin, int defaultMax, int absMin, int absMax) {
        super(name, description, new int[]{defaultMin, defaultMax});
        this.absMin = absMin;
        this.absMax = absMax;
        setValue(new int[]{defaultMin, defaultMax});
    }

    public int getMin() {
        return getValue()[0];
    }

    public int getMax() {
        return getValue()[1];
    }

    public int getAbsMin() {
        return absMin;
    }

    public int getAbsMax() {
        return absMax;
    }

    public void setMin(int v) {
        setValue(new int[]{v, getValue()[1]});
    }

    public void setMax(int v) {
        setValue(new int[]{getValue()[0], v});
    }

    /** Uniformly random value inside the current range. */
    public int random() {
        int lo = getMin(), hi = getMax();
        if (hi <= lo) return lo;
        return lo + (int) (Math.random() * (hi - lo + 1));
    }

    @Override
    public void setValue(int[] v) {
        if (v == null || v.length < 2) return;
        int lo = clamp(v[0]);
        int hi = clamp(v[1]);
        if (lo > hi) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        super.setValue(new int[]{lo, hi});
    }

    private int clamp(int v) {
        return Math.max(absMin, Math.min(absMax, v));
    }
}
