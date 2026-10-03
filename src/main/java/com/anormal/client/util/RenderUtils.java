package com.anormal.client.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RenderUtils {
    private static final List<Method> candidateMethods = new ArrayList<>();
    private static Method successfulMethod = null;
    private static boolean initialized = false;

    private static synchronized void initMethods(DrawContext context) {
        if (initialized) return;
        initialized = true;
        try {
            for (Method m : context.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 6) {
                    boolean p0Match = p[0].getName().contains("TextRenderer") || p[0].getName().contains("class_327");
                    boolean p1Match = p[1] == String.class || p[1].getName().contains("Text") || p[1].getName().contains("class_2561") || p[1].getName().contains("class_5481");
                    boolean coordsMatch = p[2] == int.class && p[3] == int.class && p[4] == int.class && p[5] == boolean.class;
                    if (p0Match && p1Match && coordsMatch) {
                        m.setAccessible(true);
                        candidateMethods.add(m);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void drawText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color, boolean shadow) {
        if (context == null || textRenderer == null || text == null) return;

        if (!initialized) {
            initMethods(context);
        }

        if (successfulMethod != null) {
            try {
                Class<?> p1 = successfulMethod.getParameterTypes()[1];
                Object textArg;
                if (p1 == String.class) {
                    textArg = text;
                } else if (p1.getName().contains("OrderedText") || p1.getName().contains("class_5481")) {
                    textArg = Text.literal(text).asOrderedText();
                } else {
                    textArg = Text.literal(text);
                }
                successfulMethod.invoke(context, textRenderer, textArg, x, y, color, shadow);
                return;
            } catch (Throwable t) {
                successfulMethod = null;
            }
        }

        for (Method m : candidateMethods) {
            try {
                Class<?> p1 = m.getParameterTypes()[1];
                Object textArg;
                if (p1 == String.class) {
                    textArg = text;
                } else if (p1.getName().contains("OrderedText") || p1.getName().contains("class_5481")) {
                    textArg = Text.literal(text).asOrderedText();
                } else {
                    textArg = Text.literal(text);
                }
                m.invoke(context, textRenderer, textArg, x, y, color, shadow);
                successfulMethod = m;
                return;
            } catch (Throwable ignored) {}
        }
    }

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

    public static void drawDarkPanel(DrawContext context, int x1, int y1, int x2, int y2, int headerHeight, int headerColor, int bodyColor, int accentLineColor) {
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
