package com.anormal.client.rotation;

/** Straight line, no easing - the rawest option. */
final class Linear implements Rotator {

    @Override
    public Rotation step(Rotation from, Rotation target, RotationConfig config, float deltaTicks) {
        float factor = Math.max(0.05f, Math.min(1.0f, deltaTicks));
        float speed = config.scaled(0.06f, 0.65f) * factor;
        return from.moved(from.yawTo(target) * speed, from.pitchTo(target) * speed);
    }
}
