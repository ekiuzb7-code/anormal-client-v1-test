package com.lucidclient.ui.util;

import com.lucidclient.ui.theme.Colors;

/**
 * Color manipulation utilities.
 */
public final class ColorUtils {
    private ColorUtils() {}

    public static int withAlpha(int rgb, int alpha) {
        return Colors.withAlpha(rgb, alpha);
    }

    public static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static int getRed(int argb) { return (argb >> 16) & 0xFF; }
    public static int getGreen(int argb) { return (argb >> 8) & 0xFF; }
    public static int getBlue(int argb) { return argb & 0xFF; }
    public static int getAlpha(int argb) { return (argb >> 24) & 0xFF; }

    /**
     * Convert a hex string like "#FFFFFF" or "FFFFFF" to an opaque ARGB int.
     */
    public static int fromHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) {
            return 0xFF000000 | Integer.parseUnsignedInt(h, 16);
        } else if (h.length() == 8) {
            return (int) Long.parseUnsignedLong(h, 16);
        }
        throw new IllegalArgumentException("Invalid hex color: " + hex);
    }
}
