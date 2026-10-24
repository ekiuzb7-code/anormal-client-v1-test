package dev.zaza.client.render;

import dev.zaza.client.util.Colors;

public final class Opacity {
    private static float current = 1f;

    private Opacity() {}

    public static void with(float alpha, Runnable draw) {
        float previous = current;
        current = previous * Math.clamp(alpha, 0f, 1f);
        try {
            draw.run();
        } finally {
            current = previous;
        }
    }

    public static int apply(int argb) {
        return current >= 1f ? argb : Colors.withAlpha(argb, current);
    }
}
