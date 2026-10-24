package com.anormal.client.rotation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * WindMouse - humanised aim movement.
 *
 * <p>Instead of moving along a straight line the cursor is treated as a particle with velocity,
 * pulled towards the target by "gravity" and pushed around by a slowly decaying "wind". That is
 * what makes the movement look hand-made rather than robotic, and it is the reason this strategy
 * exists in Cold Client / trident.</p>
 *
 * <p>Ported verbatim from the original (the constants matter - they were tuned by feel).</p>
 */
final class WindMouse implements Rotator {

    private static final float FASTEST = 14.0f;
    private static final float SLOWEST = 1.0f;
    private static final float GRAVITY = 9.0f / 15.0f;
    private static final float WIND = 3.0f / 15.0f;
    private static final float SETTLE = 12.0f / 15.0f;
    private static final float FLOOR = 3.0f / 15.0f;
    private static final float FLOOR_TOLERANCE = 1.0001f;
    private static final float DONE = 1.0f / 15.0f;
    private static final float FULL_TURN = 60.0f;
    private static final float SHORT_PACE = 0.4f;
    private static final float PACE_JITTER = 0.15f;
    private static final float SQRT3 = 1.7320508f;
    private static final float SQRT5 = 2.236068f;

    private Rotation node;
    private Rotation next;
    private Rotation expected;
    private float phase;
    private float velocityYaw;
    private float velocityPitch;
    private float windYaw;
    private float windPitch;
    private float cap;
    private float pace = SHORT_PACE;
    private boolean travelling;
    private boolean settled;

    @Override
    public Rotation step(Rotation from, Rotation target, RotationConfig config, float deltaTicks) {
        if (config.smoothness() <= 0.0f) {
            rest();
            next = null;
            return from.moved(from.yawTo(target), from.pitchTo(target));
        }

        if (next == null) {
            node = from;
            next = advance(from, target, config);
            phase = 0.0f;
        } else {
            // The player may have moved the mouse in between; keep the path anchored to reality.
            float driftYaw = expected.yawTo(from);
            float driftPitch = expected.pitchTo(from);
            if (driftYaw != 0.0f || driftPitch != 0.0f) {
                node = node.moved(driftYaw, driftPitch);
                next = next.moved(driftYaw, driftPitch);
            }
        }

        phase += deltaTicks;
        while (phase >= 1.0f) {
            phase -= 1.0f;
            node = next;
            next = advance(node, target, config);
        }

        expected = lerp(node, next, phase);
        return expected;
    }

    private Rotation advance(Rotation from, Rotation target, RotationConfig config) {
        float deltaYaw = from.yawTo(target);
        float deltaPitch = from.pitchTo(target);
        float distance = Rotation.length(deltaYaw, deltaPitch);
        if (distance < 1.0E-4f) return target;

        float base = config.scaled(SLOWEST, FASTEST);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (!travelling || (settled && distance >= base * SETTLE)) {
            float reach = Math.min(1.0f, distance / FULL_TURN);
            pace = (SHORT_PACE + (1.0f - SHORT_PACE) * reach)
                    * random.nextFloat(1.0f - PACE_JITTER, 1.0f + PACE_JITTER);
            cap = base * pace;
            travelling = true;
            settled = false;
        }

        float speed = base * pace;
        if (distance < speed * DONE) {
            rest();
            return from.moved(deltaYaw, deltaPitch);
        }

        if (distance >= speed * SETTLE) {
            float gust = Math.min(speed * WIND, distance) / SQRT5;
            windYaw = windYaw / SQRT3 + random.nextFloat(-1.0f, 1.0f) * gust;
            windPitch = windPitch / SQRT3 + random.nextFloat(-1.0f, 1.0f) * gust;
        } else {
            windYaw /= SQRT3;
            windPitch /= SQRT3;
            if (cap < speed * FLOOR * FLOOR_TOLERANCE) {
                cap = speed * (FLOOR + FLOOR * random.nextFloat());
            } else {
                cap /= SQRT5;
            }
            settled = true;
        }

        float gravity = speed * GRAVITY;
        velocityYaw += windYaw + gravity * deltaYaw / distance;
        velocityPitch += windPitch + gravity * deltaPitch / distance;

        float magnitude = Rotation.length(velocityYaw, velocityPitch);
        if (magnitude > cap) {
            float clipped = cap * (0.5f + 0.5f * random.nextFloat());
            velocityYaw *= clipped / magnitude;
            velocityPitch *= clipped / magnitude;
        }

        return from.moved(velocityYaw, velocityPitch);
    }

    private void rest() {
        windYaw = 0.0f;
        windPitch = 0.0f;
        velocityYaw = 0.0f;
        velocityPitch = 0.0f;
        travelling = false;
        settled = false;
    }

    private static Rotation lerp(Rotation from, Rotation to, float phase) {
        if (phase <= 0.0f) return from;
        return new Rotation(from.yaw() + (to.yaw() - from.yaw()) * phase,
                from.pitch() + (to.pitch() - from.pitch()) * phase);
    }
}
