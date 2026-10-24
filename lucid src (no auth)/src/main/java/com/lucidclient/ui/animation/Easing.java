package com.lucidclient.ui.animation;

/**
 * Easing functions used across Lucid Client.
 * Input is normalized progress 0..1, output is eased progress 0..1.
 */
public enum Easing {
    LINEAR {
        @Override public float apply(float t) { return t; }
    },
    EASE_OUT_QUAD {
        @Override public float apply(float t) { return 1f - (1f - t) * (1f - t); }
    },
    EASE_IN_QUAD {
        @Override public float apply(float t) { return t * t; }
    },
    EASE_IN_OUT_QUAD {
        @Override public float apply(float t) { return t < 0.5f ? 2 * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f; }
    },
    EASE_OUT_CUBIC {
        @Override public float apply(float t) { return 1f - (float) Math.pow(1f - t, 3); }
    },
    EASE_IN_OUT_CUBIC {
        @Override public float apply(float t) { return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f; }
    },
    EASE_OUT_QUART {
        @Override public float apply(float t) { return 1f - (float) Math.pow(1f - t, 4); }
    },
    EASE_OUT_EXPO {
        @Override public float apply(float t) { return t == 1f ? 1f : 1f - (float) Math.pow(2f, -10f * t); }
    },
    /** Subtle overshoot — used for opening pop-in. */
    EASE_OUT_BACK {
        private final float c1 = 1.70158f;
        private final float c3 = c1 + 1f;
        @Override public float apply(float t) {
            return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
        }
    };

    public abstract float apply(float t);
}
