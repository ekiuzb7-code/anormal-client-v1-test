package com.anormal.client.util;

import java.awt.Color;

public class ColorUtils {

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    public static int rainbow(int delay, float saturation, float brightness) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 20.0);
        rainbowState %= 360;
        return Color.HSBtoRGB((float) (rainbowState / 360.0), saturation, brightness);
    }

    public static int interpolateColor(int color1, int color2, float fraction) {
        float f = Math.max(0, Math.min(1, fraction));
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * f);
        int r = (int) (r1 + (r2 - r1) * f);
        int g = (int) (g1 + (g2 - g1) * f);
        int b = (int) (b1 + (b2 - b1) * f);

        return rgba(r, g, b, a);
    }
}
