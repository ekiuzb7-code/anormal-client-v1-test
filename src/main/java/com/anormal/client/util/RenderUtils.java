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

    // ---- Measurement mode -------------------------------------------------
    // The HUD editor switches this on while a module renders, so it can frame
    // exactly what the module actually drew instead of guessing a size.
    private static boolean measuring = false;
    private static int mMinX, mMinY, mMaxX, mMaxY;

    public static void beginMeasure() {
        measuring = true;
        mMinX = Integer.MAX_VALUE;
        mMinY = Integer.MAX_VALUE;
        mMaxX = Integer.MIN_VALUE;
        mMaxY = Integer.MIN_VALUE;
    }

    /** @return {x, y, width, height} of everything drawn, or null if nothing was drawn. */
    public static int[] endMeasure() {
        measuring = false;
        if (mMaxX < mMinX || mMaxY < mMinY) return null;
        return new int[]{mMinX, mMinY, mMaxX - mMinX, mMaxY - mMinY};
    }

    private static void record(int x1, int y1, int x2, int y2) {
        if (!measuring) return;
        if (x1 < mMinX) mMinX = x1;
        if (y1 < mMinY) mMinY = y1;
        if (x2 > mMaxX) mMaxX = x2;
        if (y2 > mMaxY) mMaxY = y2;
    }

    // -----------------------------------------------------------------------

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

        if (measuring) {
            try {
                record(x, y, x + textRenderer.getWidth(text), y + textRenderer.fontHeight + (shadow ? 1 : 0));
            } catch (Throwable ignored) {
            }
        }

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
        record(x1, y1, x2, y2);
        context.fill(x1, y1, x2, y2, color);
    }

    public static void drawBorder(DrawContext context, int x1, int y1, int x2, int y2, int borderThickness, int color) {
        record(x1, y1, x2, y2);
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

    public static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color, float width) {
        if (context == null) return;

        // ---- Clip to the screen -------------------------------------------
        // Without this, a marker line that stretches far off-screen costs one
        // fill() per pixel of its full length. Marker modules draw hundreds of
        // blocks per frame, which is what froze the game.
        int[] clip = clipToScreen(x1, y1, x2, y2);
        if (clip == null) return;
        x1 = clip[0];
        y1 = clip[1];
        x2 = clip[2];
        y2 = clip[3];

        int thickness = Math.max(1, (int) Math.ceil(width));
        int dx = x2 - x1;
        int dy = y2 - y1;
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        // ---- Axis-aligned fast paths: one fill instead of N -----------------
        if (ady == 0) {
            context.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + thickness, color);
            return;
        }
        if (adx == 0) {
            context.fill(x1, Math.min(y1, y2), x1 + thickness, Math.max(y1, y2) + 1, color);
            return;
        }

        // ---- General case: DDA with horizontal run merging ------------------
        boolean steep = ady > adx;
        int steps = steep ? ady : adx;
        if (steps <= 0) return;

        float stepX = (float) dx / steps;
        float stepY = (float) dy / steps;

        if (steep) {
            // One fill per row.
            for (int i = 0; i <= steps; i++) {
                int x = Math.round(x1 + stepX * i);
                int y = Math.round(y1 + stepY * i);
                context.fill(x, y, x + thickness, y + 1, color);
            }
        } else {
            // Merge consecutive pixels on the same row into a single fill.
            int runStartX = Math.round(x1);
            int runY = Math.round(y1);
            int prevX = runStartX;
            for (int i = 1; i <= steps; i++) {
                int x = Math.round(x1 + stepX * i);
                int y = Math.round(y1 + stepY * i);
                if (y != runY) {
                    context.fill(Math.min(runStartX, prevX), runY,
                            Math.max(runStartX, prevX) + thickness, runY + 1, color);
                    runStartX = x;
                    runY = y;
                }
                prevX = x;
            }
            context.fill(Math.min(runStartX, prevX), runY,
                    Math.max(runStartX, prevX) + thickness, runY + 1, color);
        }
    }

    /** Screen size in scaled coordinates. */
    public static int screenWidth() {
        try {
            return net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth();
        } catch (Throwable t) {
            return 1920;
        }
    }

    public static int screenHeight() {
        try {
            return net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
        } catch (Throwable t) {
            return 1080;
        }
    }

    /**
     * Liang-Barsky clip of a segment against the screen rectangle.
     *
     * @return clipped {@code {x1, y1, x2, y2}}, or {@code null} when the
     *         segment lies entirely outside the screen.
     */
    private static int[] clipToScreen(int x1, int y1, int x2, int y2) {
        int w = screenWidth();
        int h = screenHeight();
        // Small margin so lines touching the border are not visibly cut short.
        double minX = -2, minY = -2, maxX = w + 2, maxY = h + 2;

        double dx = x2 - x1;
        double dy = y2 - y1;
        double t0 = 0.0, t1 = 1.0;

        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - minX, maxX - x1, y1 - minY, maxY - y1};

        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null; // parallel and outside
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0.0) {
                    if (r > t1) return null;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return null;
                    if (r < t1) t1 = r;
                }
            }
        }

        return new int[]{
                (int) Math.round(x1 + t0 * dx),
                (int) Math.round(y1 + t0 * dy),
                (int) Math.round(x1 + t1 * dx),
                (int) Math.round(y1 + t1 * dy)
        };
    }

    public static int toIntColor(float[] rgb, float alpha) {
        int r = (int) (rgb[0] * 255);
        int g = (int) (rgb[1] * 255);
        int b = (int) (rgb[2] * 255);
        int a = (int) (alpha * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------
    //  World-space block helpers (shared by every block-ESP module)
    // ------------------------------------------------------------------

    /** Draws a 3D wireframe outline around a block (visible through walls). */
    public static void drawBlockOutline(DrawContext context, net.minecraft.util.math.BlockPos pos,
                                        int col, float tickDelta, int lineWidth) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        double e = 0.02;
        int[][] c = {
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x - e, y - e, z - e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x + 1 + e, y - e, z - e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x + 1 + e, y - e, z + 1 + e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x - e, y - e, z + 1 + e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x - e, y + 1 + e, z - e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x + 1 + e, y + 1 + e, z - e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x + 1 + e, y + 1 + e, z + 1 + e), tickDelta),
                ProjectionUtil.project(new net.minecraft.util.math.Vec3d(x - e, y + 1 + e, z + 1 + e), tickDelta)
        };

        // ---- Cull blocks that are entirely off-screen -----------------------
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (int[] p : c) {
            if (p == null) continue;
            any = true;
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        if (!any) return;
        if (maxX < 0 || maxY < 0 || minX > screenWidth() || minY > screenHeight()) return;

        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        int w = Math.max(1, lineWidth);
        for (int[] ed : edges) {
            int[] a = c[ed[0]], b = c[ed[1]];
            if (a == null || b == null) continue;
            drawLine(context, a[0], a[1], b[0], b[1], col, w);
        }
    }

    /** Draws a translucent screen-space fill covering a block's projected bounds. */
    public static void drawBlockFill(DrawContext context, net.minecraft.util.math.BlockPos pos,
                                     int col, float tickDelta, int alpha) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        double e = 0.02;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? x - e : x + 1 + e;
            double py = (i & 2) == 0 ? y - e : y + 1 + e;
            double pz = (i & 4) == 0 ? z - e : z + 1 + e;
            int[] s = ProjectionUtil.project(new net.minecraft.util.math.Vec3d(px, py, pz), tickDelta);
            if (s == null) continue;
            any = true;
            minX = Math.min(minX, s[0]);
            minY = Math.min(minY, s[1]);
            maxX = Math.max(maxX, s[0]);
            maxY = Math.max(maxY, s[1]);
        }
        if (!any) return;
        if (maxX < 0 || maxY < 0 || minX > screenWidth() || minY > screenHeight()) return;
        int a = Math.max(1, Math.min(255, alpha));
        context.fill(minX, minY, maxX, maxY, (a << 24) | (col & 0x00FFFFFF));
    }

    /**
     * Projects a block's 8 corners and returns its screen bounding rectangle,
     * or {@code null} when the block is off-screen / behind the camera.
     *
     * @return {@code {minX, minY, maxX, maxY}}
     */
    public static int[] projectBlockBounds(net.minecraft.util.math.BlockPos pos, float tickDelta) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? x : x + 1;
            double py = (i & 2) == 0 ? y : y + 1;
            double pz = (i & 4) == 0 ? z : z + 1;
            int[] s = ProjectionUtil.project(new net.minecraft.util.math.Vec3d(px, py, pz), tickDelta);
            if (s == null) continue;
            any = true;
            minX = Math.min(minX, s[0]);
            minY = Math.min(minY, s[1]);
            maxX = Math.max(maxX, s[0]);
            maxY = Math.max(maxY, s[1]);
        }
        if (!any) return null;
        if (maxX < 0 || maxY < 0 || minX > screenWidth() || minY > screenHeight()) return null;
        return new int[]{minX, minY, maxX, maxY};
    }
}
