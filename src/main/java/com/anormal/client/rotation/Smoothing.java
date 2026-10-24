package com.anormal.client.rotation;

import java.util.function.Supplier;

/** Available smoothing strategies. */
public enum Smoothing {
    EASE_OUT_CUBIC("Ease Out Cubic", EaseOutCubic::new),
    WIND_MOUSE("WindMouse", WindMouse::new),
    LINEAR("Linear", Linear::new);

    private final String label;
    private final Supplier<Rotator> factory;

    Smoothing(String label, Supplier<Rotator> factory) {
        this.label = label;
        this.factory = factory;
    }

    public String label() {
        return label;
    }

    public Rotator create() {
        return factory.get();
    }

    public static final String[] NAMES = {"Ease Out Cubic", "WindMouse", "Linear"};

    public static Smoothing byName(String name) {
        for (Smoothing s : values()) {
            if (s.label.equalsIgnoreCase(name)) return s;
        }
        return EASE_OUT_CUBIC;
    }
}
