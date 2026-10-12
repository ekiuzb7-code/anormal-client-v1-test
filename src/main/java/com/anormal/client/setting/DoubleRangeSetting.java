package com.anormal.client.setting;

/**
 * A bounded decimal range (min..max), e.g. LiquidBounce's {@code floatRange("Range", 2f..5f, 0f..10f)}.
 * The value is stored as {@code double[]{min, max}}.
 */
public class DoubleRangeSetting extends Setting<double[]> {
    private final double absMin;
    private final double absMax;
    private final double step;

    public DoubleRangeSetting(String name, String description, double defaultMin, double defaultMax,
                              double absMin, double absMax, double step) {
        super(name, description, new double[]{defaultMin, defaultMax});
        this.absMin = absMin;
        this.absMax = absMax;
        this.step = step;
        setValue(new double[]{defaultMin, defaultMax});
    }

    public double getMin() {
        return getValue()[0];
    }

    public double getMax() {
        return getValue()[1];
    }

    public double getAbsMin() {
        return absMin;
    }

    public double getAbsMax() {
        return absMax;
    }

    public double getStep() {
        return step;
    }

    public void setMin(double v) {
        setValue(new double[]{v, getValue()[1]});
    }

    public void setMax(double v) {
        setValue(new double[]{getValue()[0], v});
    }

    /** Uniformly random value inside the current range. */
    public double random() {
        double lo = getMin(), hi = getMax();
        if (hi <= lo) return lo;
        return lo + Math.random() * (hi - lo);
    }

    @Override
    public void setValue(double[] v) {
        if (v == null || v.length < 2) return;
        double lo = clamp(v[0]);
        double hi = clamp(v[1]);
        if (lo > hi) {
            double t = lo;
            lo = hi;
            hi = t;
        }
        super.setValue(new double[]{round(lo), round(hi)});
    }

    private double clamp(double v) {
        return Math.max(absMin, Math.min(absMax, v));
    }

    private double round(double v) {
        if (step <= 0) return v;
        double precision = 1.0 / step;
        return Math.round(v * precision) / precision;
    }
}
