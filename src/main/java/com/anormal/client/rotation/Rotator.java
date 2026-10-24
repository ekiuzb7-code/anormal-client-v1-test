package com.anormal.client.rotation;

/** Produces the next rotation from the current one. */
public interface Rotator {
    Rotation step(Rotation from, Rotation target, RotationConfig config, float deltaTicks);
}
