package com.lucidclient.ui.util;

/**
 * Generic math helpers for animations and layout.
 */
public final class MathUtils {
    private MathUtils() {}

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static int lerpInt(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    public static float map(float v, float inMin, float inMax, float outMin, float outMax) {
        return outMin + (outMax - outMin) * ((v - inMin) / (inMax - inMin));
    }

    /** Smoothstep — produces a smoother interpolation curve. */
    public static float smoothstep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
