package com.anormal.client.rotation;

/**
 * How a rotation should be applied.
 *
 * <p>{@code smoothness} is 0..1 (higher = slower / smoother) and {@link #scaled} maps it onto the
 * slowest/fastest bound of each smoothing strategy.</p>
 */
public record RotationConfig(float smoothness, Smoothing smoothing, boolean silent) {

    public static RotationConfig visible(float smoothness, Smoothing smoothing) {
        return new RotationConfig(smoothness, smoothing, false);
    }

    public static RotationConfig silent(float smoothness, Smoothing smoothing) {
        return new RotationConfig(smoothness, smoothing, true);
    }

    /** Linear blend from {@code slow} (smoothness 1) to {@code fast} (smoothness 0). */
    public float scaled(float slow, float fast) {
        float eager = (1.0f - smoothness) * (1.0f - smoothness);
        return slow + (fast - slow) * eager;
    }
}
