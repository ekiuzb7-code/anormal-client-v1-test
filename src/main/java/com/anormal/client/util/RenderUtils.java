package com.anormal.client.util;

import net.minecraft.client.gui.DrawContext;

public class RenderUtils {

    public static void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public static void drawBorder(DrawContext context, int x1, int y1, int x2, int y2, int borderThickness, int color) {
        // Top
        context.fill(x1, y1, x2, y1 + borderThickness, color);
        // Bottom
        context.fill(x1, y2 - borderThickness, x2, y2, color);
        // Left
        context.fill(x1, y1 + borderThickness, x1 + borderThickness, y2 - borderThickness, color);
        // Right
        context.fill(x2 - borderThickness, y1 + borderThickness, x2, y2 - borderThickness, color);
    }

    public static void drawGlassPanel(DrawContext context, int x1, int y1, int x2, int y2, int bgColor, int borderColor, int glowColor) {
        // Subtle glow outer border
        drawBorder(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 1, glowColor);
        // Inner background
        context.fill(x1, y1, x2, y2, bgColor);
        // Main sharp border
        drawBorder(context, x1, y1, x2, y2, 1, borderColor);
    }

    public static void drawVapePanel(DrawContext context, int x1, int y1, int x2, int y2, int headerHeight, int headerColor, int bodyColor, int accentLineColor) {
        // Body background
        context.fill(x1, y1 + headerHeight, x2, y2, bodyColor);
        // Header background
        context.fill(x1, y1, x2, y1 + headerHeight, headerColor);
        // Accent line below header
        context.fill(x1, y1 + headerHeight - 1, x2, y1 + headerHeight, accentLineColor);
        // Dark outer outline
        drawBorder(context, x1, y1, x2, y2, 1, ColorUtils.rgba(10, 10, 10, 240));
    }
}
