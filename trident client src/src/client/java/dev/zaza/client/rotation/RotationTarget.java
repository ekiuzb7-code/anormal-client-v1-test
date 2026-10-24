package dev.zaza.client.rotation;

@FunctionalInterface
public interface RotationTarget {
    Rotation at(float partialTick);

    static RotationTarget fixed(Rotation rotation) {
        return partialTick -> rotation;
    }
}
