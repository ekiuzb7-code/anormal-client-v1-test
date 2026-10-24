package dev.zaza.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zaza.client.render.font.Font;
import dev.zaza.client.render.font.Fonts;
import dev.zaza.client.render.state.Pipelines;
import dev.zaza.client.render.state.QuadState;
import dev.zaza.client.render.state.RectState;
import dev.zaza.client.render.state.Submit;
import dev.zaza.client.render.state.TextState;
import dev.zaza.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

public final class Draw {
    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_RIGHT = 4;
    public static final int BOTTOM_LEFT = 8;
    public static final int ALL_CORNERS = 15;

    private Draw() {}

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        rect(graphics, x, y, width, height, radius, color, Pipelines.RECT);
    }

    public static void rect(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                            float radius, int color) {
        if (width <= 0 || height <= 0) return;
        rect(graphics, Math.round(x), Math.round(y), Math.max(1, Math.round(width)),
                Math.max(1, Math.round(height)), Math.max(0, Math.round(radius)), color);
    }

    public static void rect(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                            float radius, int color, RenderPipeline pipeline) {
        if (width <= 0 || height <= 0) return;
        rect(graphics, Math.round(x), Math.round(y), Math.max(1, Math.round(width)),
                Math.max(1, Math.round(height)), Math.max(0, Math.round(radius)), color, color, pipeline);
    }

    public static void gradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                                int top, int bottom) {
        rect(graphics, x, y, width, height, radius, top, bottom, Pipelines.RECT);
    }

    public static void bordered(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                float radius, int fill, int border) {
        int r = Math.max(0, Math.round(radius));
        int h = Math.max(1, Math.round(height));
        if (!Colors.transparent(fill)) rect(graphics, x, y, width, h, r, fill);
        border(graphics, x, y, width, h, r, border);
    }

    /**
     * One-pixel rounded outline: an outer rounded rect with the inner area
     * subtracted in the shader, leaving a crisp hairline border.
     */
    public static void border(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                              float radius, int color) {
        if (width <= 0 || height <= 0 || Colors.transparent(color)) return;
        int xi = Math.round(x);
        int yi = Math.round(y);
        int w = Math.max(1, Math.round(width));
        int h = Math.max(1, Math.round(height));
        int r = Math.max(0, Math.round(radius));
        color = Opacity.apply(color);
        Submit.submit(graphics, new RectState(new org.joml.Matrix3x2f(graphics.pose()),
                xi, yi, w, h, r, color, color, Pipelines.BORDER));
    }

    /** Rotated line of the given thickness, via an arbitrary quad. */
    public static void line(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1,
                            float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001f || thickness <= 0f || Colors.transparent(color)) return;
        float half = thickness / 2f;
        float nx = -dy / length * half;
        float ny = dx / length * half;
        color = Opacity.apply(color);
        Submit.submit(graphics, new QuadState(new org.joml.Matrix3x2f(graphics.pose()),
                x0 - nx, y0 - ny, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, color));
    }

    public static void icon(GuiGraphicsExtractor graphics, int codepoint, float x, float y, float size, int color) {
        color = Opacity.apply(color);
        if (size <= 0 || !Float.isFinite(size) || Colors.transparent(color)) return;
        Font font = Fonts.MATERIAL_ICONS;
        Font.Glyph glyph = font.glyph(codepoint);
        Font.Bounds plane = glyph.planeBounds();
        if (plane == null || glyph.atlasBounds() == null) return;
        Font.Bounds uv = font.uv(glyph);
        float height = (plane.bottom() - plane.top()) * size;
        float top = y + (size - height) * 0.5f + plane.top() * size;
        var quad = new TextState.Quad(
                x, top, x + (plane.right() - plane.left()) * size, top + height,
                uv.left(), uv.top(), uv.right(), uv.bottom(), color);
        Submit.submit(graphics, TextState.of(graphics, font.texture(), List.of(quad)));
    }

    private static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                             int color, int bottomColor, RenderPipeline pipeline) {
        color = Opacity.apply(color);
        bottomColor = Opacity.apply(bottomColor);
        if (width <= 0 || height <= 0 || (Colors.transparent(color) && Colors.transparent(bottomColor))) return;
        if (width > Short.MAX_VALUE || height > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Rectangle dimensions exceed the vertex format's range");
        }
        int limit = Math.min(width, height) / 2;
        Submit.submit(graphics, new RectState(new org.joml.Matrix3x2f(graphics.pose()), x, y, width, height,
                (int) Math.clamp(radius, 0, Math.max(0, limit)), color, bottomColor, pipeline));
    }
}
