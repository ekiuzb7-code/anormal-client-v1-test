package com.anormal.client.rotation;

/** Snaps to the target over a fixed duration with a cubic ease-out. */
final class EaseOutCubic implements Rotator {

    private static final float SLOWEST_TICKS = 40.0f;

    @Override
    public Rotation step(Rotation from, Rotation target, RotationConfig config, float deltaTicks) {
        float duration = config.scaled(SLOWEST_TICKS, 0.0f);
        float remaining = duration <= 0.0f ? 0.0f : 1.0f - Math.min(1.0f, deltaTicks / duration);
        float fraction = 1.0f - remaining * remaining * remaining;
        return from.moved(from.yawTo(target) * fraction, from.pitchTo(target) * fraction);
    }
}
