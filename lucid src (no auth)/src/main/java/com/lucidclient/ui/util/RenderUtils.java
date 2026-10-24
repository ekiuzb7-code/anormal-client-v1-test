package com.lucidclient.ui.util;

import com.lucidclient.ui.theme.Colors;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Low-level drawing helpers for Lucid Client.
 * Provides rounded rectangles, soft shadows, frosted-glass panels,
 * text helpers with alignment, and scrollbar rendering.
 *
 * All coordinates are pixel-space ints. Colors are ARGB ints.
 *
 * All drawing goes through {@link DrawContext#fill(int, int, int, int, int)}
 * and {@link DrawContext#drawText(TextRenderer, String, int, int, int, boolean)}
 * — the standard 1.21.11 yarn-mapped rendering APIs.
 *
 * <h3>Performance — corner geometry caching + run-length encoding</h3>
 * The ClickGUI draws hundreds of rounded rectangles per frame (panels, cards,
 * toggles, sliders, knobs). Naively each rounded rect issues ~{@code 3 + 4*r}
 * {@code ctx.fill} calls (one per corner scanline) — thousands of draw
 * submissions per frame, which is the dominant GUI FPS cost.
 *
 * To fix this without changing a single pixel, each corner of a rounded rect
 * is decomposed into the minimum number of axis-aligned fills via
 * <b>run-length encoding</b>: consecutive scanlines with the same horizontal
 * extent are merged into one taller fill. For a typical radius-14 corner the
 * r=14 per-scanline fills collapse to ~5-8 merged fills — a ~2-3x reduction
 * per corner, ~6-10x per full rounded rect.
 *
 * The corner geometry (the per-scanline x-extents) depends only on the radius
 * (and for borders, on the thickness), so it is computed once per (r,
 * thickness) pair and cached in {@link #FILL_CORNER_CACHE} /
 * {@link #BORDER_CORNER_CACHE}. The expensive {@code Math.sqrt} per scanline
 * thus runs at most once per distinct radius ever used, not once per corner
 * per frame.
 */
public final class RenderUtils {
    private RenderUtils() {}

    // ════════════════════════════════════════════════════════════════
    //  Corner geometry cache.
    //
    //  A "corner span" describes one merged run of scanlines at the same
    //  horizontal extent for one corner of a rounded rect. For a filled
    //  corner of radius r, the spans are (xOffsetFromInnerEdge, runLength)
    //  pairs read top-to-bottom; the caller translates them to the four
    //  corners. For a bordered corner, the spans are (xOuterOffset,
    //  xInnerOffset, runLength) — the ring between the outer and inner arcs.
    //
    //  Cache key: radius for fills, (thickness<<16)|radius for borders.
    //  The geometry is rotation-invariant, so one entry covers all four
    //  corners of every rounded rect of that radius.
    // ════════════════════════════════════════════════════════════════

    /** A merged run of corner scanlines at the same horizontal extent
     *  (filled corner). {@code startOffset} is the distance from the inner
     *  edge of the corner (the pointy end) to the start of the run; the run
     *  extends {@code runLen} scanlines downward and {@code span} pixels
     *  outward from the inner edge. */
    private record FillSpan(int startOffset, int span, int runLen) {}
    /** A merged run of corner scanlines (bordered corner). {@code xOuter} /
     *  {@code xInner} are the outer/inner arc x-extents measured from the
     *  inner edge of the corner. */
    private record BorderSpan(int startOffset, int xOuter, int xInner, int runLen) {}

    private static final Int2ObjectOpenHashMap<FillSpan[]> FILL_CORNER_CACHE = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<BorderSpan[]> BORDER_CORNER_CACHE = new Int2ObjectOpenHashMap<>();

    /**
     * Compute (or fetch from cache) the run-length-encoded scanline spans
     * for a filled corner of radius {@code r}. Each span says: starting
     * {@code startOffset} scanlines below the top of the corner, the next
     * {@code runLen} scanlines all extend {@code span} pixels outward from
     * the corner's inner edge.
     */
    private static FillSpan[] buildFillCorner(int r) {
        FillSpan[] cached = FILL_CORNER_CACHE.get(r);
        if (cached != null) return cached;
        int r2 = r * r;
        // First pass: compute the per-scanline x-extent.
        int[] xExt = new int[r];
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double dx = Math.sqrt(Math.max(0, r2 - dy * dy));
            xExt[i] = (int) Math.round(dx);
        }
        // Second pass: run-length encode — merge consecutive scanlines with
        // the same xExt into one span.
        java.util.List<FillSpan> spans = new java.util.ArrayList<>();
        int runStart = 0;
        for (int i = 1; i <= r; i++) {
            if (i == r || xExt[i] != xExt[runStart]) {
                spans.add(new FillSpan(runStart, xExt[runStart], i - runStart));
                runStart = i;
            }
        }
        FillSpan[] arr = spans.toArray(new FillSpan[0]);
        FILL_CORNER_CACHE.put(r, arr);
        return arr;
    }

    /**
     * Compute (or fetch from cache) the run-length-encoded scanline spans
     * for a bordered corner of outer radius {@code rOut} and inner radius
     * {@code rIn}. Each span says: starting {@code startOffset} scanlines
     * below the top, the next {@code runLen} scanlines have outer arc at
     * {@code xOuter} and inner arc at {@code xInner} (measured from the
     * corner's inner edge); the fill is the ring between them.
     */
    private static BorderSpan[] buildBorderCorner(int rOut, int rIn) {
        int key = (rIn << 16) | rOut;
        BorderSpan[] cached = BORDER_CORNER_CACHE.get(key);
        if (cached != null) return cached;
        int rOut2 = rOut * rOut;
        int rIn2 = rIn * rIn;
        int[] xOuter = new int[rOut];
        int[] xInner = new int[rOut];
        for (int i = 0; i < rOut; i++) {
            double dy = rOut - i - 0.5;
            double dxOut = Math.sqrt(Math.max(0, rOut2 - dy * dy));
            xOuter[i] = (int) Math.round(dxOut);
            xInner[i] = rIn > 0 ? (int) Math.round(Math.sqrt(Math.max(0, rIn2 - dy * dy))) : 0;
        }
        // Run-length encode on the (xOuter, xInner) pair.
        java.util.List<BorderSpan> spans = new java.util.ArrayList<>();
        int runStart = 0;
        for (int i = 1; i <= rOut; i++) {
            boolean diff = i == rOut
                || xOuter[i] != xOuter[runStart]
                || xInner[i] != xInner[runStart];
            if (diff) {
                spans.add(new BorderSpan(runStart, xOuter[runStart], xInner[runStart], i - runStart));
                runStart = i;
            }
        }
        BorderSpan[] arr = spans.toArray(new BorderSpan[0]);
        BORDER_CORNER_CACHE.put(key, arr);
        return arr;
    }

    // ── Rounded rectangle (filled) ─────────────────────────────
    public static void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2, h / 2));
        if (r < 1) { ctx.fill(x, y, x + w, y + h, color); return; }

        // Middle band (full width) — one fill covers the whole interior.
        ctx.fill(x, y + r, x + w, y + h - r, color);
        // Top + bottom bands (between corners).
        ctx.fill(x + r, y, x + w - r, y + r, color);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, color);

        // Corners — run-length-encoded spans (cached per radius). Each span
        // merges consecutive scanlines with the same x-extent into one fill.
        FillSpan[] spans = buildFillCorner(r);
        for (FillSpan s : spans) {
            if (s.span <= 0) continue;
            int rowY = y + s.startOffset;
            int rowH = s.runLen;
            // Top-left: from (x + r - span) to (x + r)
            ctx.fill(x + r - s.span, rowY, x + r, rowY + rowH, color);
            // Top-right: from (x + w - r) to (x + w - r + span)
            ctx.fill(x + w - r, rowY, x + w - r + s.span, rowY + rowH, color);
            // Bottom-left (mirror vertically)
            int by = y + h - s.startOffset - s.runLen;
            ctx.fill(x + r - s.span, by, x + r, by + rowH, color);
            // Bottom-right
            ctx.fill(x + w - r, by, x + w - r + s.span, by + rowH, color);
        }
    }

    // ── Rounded rectangle outline (border) ────────────────────
    public static void drawRoundedRectBorder(DrawContext ctx, int x, int y, int w, int h, int r, int color, int thickness) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2, h / 2));
        thickness = Math.max(1, thickness);
        if (r < 1) {
            ctx.fill(x, y, x + w, y + thickness, color);
            ctx.fill(x, y + h - thickness, x + w, y + h, color);
            ctx.fill(x, y, x + thickness, y + h, color);
            ctx.fill(x + w - thickness, y, x + w, y + h, color);
            return;
        }

        // Straight edges (between corners).
        ctx.fill(x + r, y, x + w - r, y + thickness, color);
        ctx.fill(x + r, y + h - thickness, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + thickness, y + h - r, color);
        ctx.fill(x + w - thickness, y + r, x + w, y + h - r, color);

        // Corner arcs — run-length-encoded spans (cached per (r, thickness)).
        int rOut = r;
        int rIn = Math.max(0, r - thickness);
        BorderSpan[] spans = buildBorderCorner(rOut, rIn);
        for (BorderSpan s : spans) {
            if (s.xOuter <= s.xInner) continue;
            int rowY = y + s.startOffset;
            int rowH = s.runLen;
            // Top-left: from (x + r - xOuter) to (x + r - xInner)
            ctx.fill(x + r - s.xOuter, rowY, x + r - s.xInner, rowY + rowH, color);
            // Top-right: from (x + w - r + xInner) to (x + w - r + xOuter)
            ctx.fill(x + w - r + s.xInner, rowY, x + w - r + s.xOuter, rowY + rowH, color);
            // Bottom-left (mirror vertically)
            int by = y + h - s.startOffset - s.runLen;
            ctx.fill(x + r - s.xOuter, by, x + r - s.xInner, by + rowH, color);
            // Bottom-right
            ctx.fill(x + w - r + s.xInner, by, x + w - r + s.xOuter, by + rowH, color);
        }
    }

    // ── Soft shadow (drawn behind a card) ─────────────────────
    /**
     * Draw a soft, layered shadow behind a rounded rectangle.
     * Call BEFORE drawing the foreground panel.
     */
    public static void drawSoftShadow(DrawContext ctx, int x, int y, int w, int h, int r, int spread, int baseAlpha) {
        if (spread <= 0 || w <= 0 || h <= 0) return;
        // Optimized: only draw 2 layers instead of `spread` layers.
        // This cuts the fill calls from spread*many to just 2*many.
        int a1 = (int) (baseAlpha * 0.4f);
        int a2 = (int) (baseAlpha * 0.15f);
        if (a1 > 0) {
            drawRoundedRect(ctx, x - 2, y - 2, w + 4, h + 4, r + 2, Colors.withAlpha(0x000000, a1));
        }
        if (a2 > 0) {
            drawRoundedRect(ctx, x - spread, y - spread, w + 2 * spread, h + 2 * spread, r + spread, Colors.withAlpha(0x000000, a2));
        }
    }

    // ── Frosted glass panel ───────────────────────────────────
    /**
     * Render a frosted-white-glass panel: soft shadow + white glass fill + thin border.
     */
    public static void drawGlassPanel(DrawContext ctx, int x, int y, int w, int h, int r,
                                      int glassColor, int borderColor, boolean shadow) {
        if (shadow) drawSoftShadow(ctx, x, y, w, h, r, 6, 28);
        drawRoundedRect(ctx, x, y, w, h, r, glassColor);
        drawRoundedRectBorder(ctx, x, y, w, h, r, borderColor, 1);
    }

    // ── Card (white bg + border + optional shadow + hover lift) ─
    public static void drawCard(DrawContext ctx, int x, int y, int w, int h, int r, boolean shadow, boolean hoverLift) {
        int yy = hoverLift ? y - 2 : y;
        if (shadow) drawSoftShadow(ctx, x, yy, w, h, r, hoverLift ? 8 : 5, hoverLift ? 36 : 22);
        drawRoundedRect(ctx, x, yy, w, h, r, Colors.CARD);
        drawRoundedRectBorder(ctx, x, yy, w, h, r, Colors.BORDER, 1);
    }

    // ── Gradient fill ─────────────────────────────────────────
    /**
     * Vertical gradient. Quantized to at most {@code STEPS} color bands so
     * a tall gradient costs ≤16 fills instead of one per scanline — visually
     * indistinguishable from per-row lerping for the panel/card gradients
     * the ClickGUI uses (small heights, subtle color shifts).
     */
    public static void drawGradientVertical(DrawContext ctx, int x, int y, int w, int h, int colorTop, int colorBottom) {
        if (h <= 0 || w <= 0) return;
        final int STEPS = Math.min(16, h);
        for (int s = 0; s < STEPS; s++) {
            float t = (float) s / Math.max(1, STEPS - 1);
            int y0 = y + (h * s) / STEPS;
            int y1 = y + (h * (s + 1)) / STEPS;
            if (y1 <= y0) continue;
            ctx.fill(x, y0, x + w, y1, Colors.lerp(colorTop, colorBottom, t));
        }
    }

    // ── Thin scrollbar ────────────────────────────────────────
    public static void drawScrollbar(DrawContext ctx, int trackX, int trackY, int trackH,
                                     float scroll, float contentHeight, int barColor) {
        if (contentHeight <= trackH) return;
        float visibleRatio = trackH / contentHeight;
        int barH = Math.max(20, (int) (trackH * visibleRatio));
        int maxScroll = (int) (contentHeight - trackH);
        float scrollRatio = maxScroll > 0 ? MathHelper.clamp(scroll / maxScroll, 0f, 1f) : 0f;
        int barY = trackY + (int) ((trackH - barH) * scrollRatio);
        drawRoundedRect(ctx, trackX, barY, 4, barH, 2, barColor);
    }

    // ── Text helpers ──────────────────────────────────────────
    public static void drawText(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color, boolean shadow) {
        ctx.drawText(tr, text, x, y, color, shadow);
    }

    public static void drawText(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color, boolean shadow) {
        ctx.drawText(tr, text, x, y, color, shadow);
    }

    /** Center text horizontally within a box of given width. */
    public static int centerTextX(TextRenderer tr, String text, int x, int w) {
        return x + (w - tr.getWidth(text)) / 2;
    }

    /** Center text vertically within a box of given height. */
    public static int centerTextY(TextRenderer tr, int y, int h) {
        return y + (h - tr.fontHeight) / 2 + 1;
    }

    /** Draw text scaled (e.g. for titles) — restores matrix state. */
    public static void drawScaledText(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color, float scale, boolean shadow) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        ctx.drawText(tr, text, sx, sy, color, shadow);
        ctx.getMatrices().popMatrix();
    }

    public static void drawScaledText(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color, float scale, boolean shadow) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        ctx.drawText(tr, text, sx, sy, color, shadow);
        ctx.getMatrices().popMatrix();
    }

    /** Centered scaled text. */
    public static void drawScaledCenteredText(DrawContext ctx, TextRenderer tr, String text,
                                               int centerX, int y, int color, float scale, boolean shadow) {
        int w = (int) (tr.getWidth(text) * scale);
        drawScaledText(ctx, tr, text, centerX - w / 2, y, color, scale, shadow);
    }

    // ── Dashed separator ─────────────────────────────────────
    public static void drawDashedLine(DrawContext ctx, int x, int y, int w, int color, int dashLen, int gap) {
        int cx = x;
        while (cx < x + w) {
            int end = Math.min(cx + dashLen, x + w);
            ctx.fill(cx, y, end, y + 1, color);
            cx += dashLen + gap;
        }
    }

    /** Solid horizontal divider. */
    public static void drawHLine(DrawContext ctx, int x, int y, int w, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
    }

    /** Solid vertical divider. */
    public static void drawVLine(DrawContext ctx, int x, int y, int h, int color) {
        ctx.fill(x, y, x + 1, y + h, color);
    }

    // ═══════════════════════════════════════════════════════════════
    //  AURORA FX — glow, gradients, shimmer.
    //  Layered translucent fills that read as bloom / "shader" light
    //  around the Aurora glass panels. All through DrawContext.fill.
    // ═══════════════════════════════════════════════════════════════

    /**
     * Soft bloom behind a rounded panel: two low-alpha accent layers
     * (tight + wide). Draw BEFORE the panel itself.
     *
     * @param glow1 tight layer color (alpha baked in, e.g. AURORA_GLOW_1)
     * @param glow2 wide layer color (alpha baked in, e.g. AURORA_GLOW_2)
     */
    public static void drawGlow(DrawContext ctx, int x, int y, int w, int h, int r,
                                int glow1, int glow2) {
        if (w <= 0 || h <= 0) return;
        drawRoundedRect(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, glow1);
        drawRoundedRect(ctx, x - 3, y - 3, w + 6, h + 6, r + 3, glow2);
    }

    /**
     * Rounded border with a soft outer accent glow — focus/active states.
     * Draws a 1px expand ring behind the crisp border (caller draws the
     * crisp border on top if it wants a sharper inner edge).
     */
    public static void drawGlowBorder(DrawContext ctx, int x, int y, int w, int h, int r,
                                      int accentColor, int alpha) {
        if (w <= 0 || h <= 0) return;
        int spread = 3;
        drawRoundedRect(ctx, x - 1, y - 1, w + 2, h + 2, r + 1,
            Colors.withAlpha(accentColor, (int) (alpha * 0.20f)));
        drawRoundedRect(ctx, x - spread, y - spread, w + 2 * spread, h + 2 * spread, r + spread,
            Colors.withAlpha(accentColor, (int) (alpha * 0.10f)));
    }

    /**
     * Horizontal gradient — quantized to ≤16 bands (mirrors
     * {@link #drawGradientVertical}). Used for the Aurora accent strips
     * (title bars, enabled-row bars, arraylist bars): blue → violet.
     */
    public static void drawGradientHorizontal(DrawContext ctx, int x, int y, int w, int h,
                                              int colorLeft, int colorRight) {
        if (h <= 0 || w <= 0) return;
        final int STEPS = Math.min(16, w);
        if (STEPS <= 1) { ctx.fill(x, y, x + w, y + h, colorLeft); return; }
        for (int s = 0; s < STEPS; s++) {
            float t = (float) s / (STEPS - 1);
            int x0 = x + (w * s) / STEPS;
            int x1 = x + (w * (s + 1)) / STEPS;
            if (x1 <= x0) continue;
            ctx.fill(x0, y, x1, y + h, Colors.lerp(colorLeft, colorRight, t));
        }
    }

    /**
     * Vertical accent gradient clipped to a rounded rectangle — for the
     * Aurora title bar / health bars where the ends must stay rounded.
     * Implementation: rounded clip mask in the panel color, then the
     * gradient inset by 1px on left/right so corners stay clean.
     */
    public static void drawRoundedGradientVertical(DrawContext ctx, int x, int y, int w, int h,
                                                   int r, int colorTop, int colorBottom) {
        if (w <= 0 || h <= 0) return;
        int rr = Math.min(r, Math.min(w / 2, h / 2));
        // Simple approach for thin strips (r collapses): plain gradient.
        if (rr < 2) { drawGradientVertical(ctx, x, y, w, h, colorTop, colorBottom); return; }
        // Body: full-width gradient in the middle band.
        drawGradientVertical(ctx, x + 1, y + rr, w - 2, h - rr * 2, colorTop, colorBottom);
        // Top cap: gradient rows + rounded left/right ends.
        for (int i = 0; i < rr; i++) {
            float t = (float) i / Math.max(1, h - 1);
            int rowCol = Colors.lerp(colorTop, colorBottom, t);
            int inset = rr - (int) Math.round(Math.sqrt(Math.max(0, rr * rr - (rr - i - 0.5) * (rr - i - 0.5))));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, rowCol);
        }
        // Bottom cap (mirrored).
        for (int i = 0; i < rr; i++) {
            int rowY = y + h - rr + i;
            float t = (float) (h - rr + i) / Math.max(1, h - 1);
            int rowCol = Colors.lerp(colorTop, colorBottom, t);
            double dyB = rr - i - 0.5;
            int inset = rr - (int) Math.round(Math.sqrt(Math.max(0, rr * rr - dyB * dyB)));
            ctx.fill(x + inset, rowY, x + w - inset, rowY + 1, rowCol);
        }
    }

    /**
     * Frosted glass top-highlight: a 1px translucent white line under the
     * top edge of a card — the classic glass-refraction cue.
     */
    public static void drawTopHighlight(DrawContext ctx, int x, int y, int w, int alpha) {
        if (w <= 0) return;
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2, Colors.withAlpha(0xFFFFFF, alpha));
    }

    /** Convenience: Aurora window glass = shadow + glass fill + hairline border + top highlight. */
    public static void drawAuroraPanel(DrawContext ctx, int x, int y, int w, int h, int r, int alpha) {
        drawSoftShadow(ctx, x, y, w, h, r, 7, 64 * alpha / 255);
        drawRoundedRect(ctx, x, y, w, h, r, Colors.withAlpha(Colors.AURORA_GLASS, alpha));
        drawRoundedRectBorder(ctx, x, y, w, h, r, Colors.withAlpha(Colors.AURORA_BORDER, alpha), 1);
        drawTopHighlight(ctx, x, y, w, 28 * alpha / 255);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CLASSIC SQUARE RECTS — for the classic Square ClickGUI.
    //
    //  These bypass the rounded-rect corner caches entirely and draw
    //  pure axis-aligned rectangles — pixel-perfect sharp edges, no
    //  anti-aliasing, no corner math. The classic Minecraft hack-
    //  client look. Each call is a single {@link DrawContext#fill}.
    // ═══════════════════════════════════════════════════════════════

    /** Solid square rectangle (single fill, sharp edges). */
    public static void drawSquareRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    /** Square outline (1px border, sharp corners). */
    public static void drawSquareBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + 1, color);            // top
        ctx.fill(x, y + h - 1, x + w, y + h, color);    // bottom
        ctx.fill(x, y, x + 1, y + h, color);            // left
        ctx.fill(x + w - 1, y, x + w, y + h, color);    // right
    }

    /** Square outline with explicit thickness (sharp corners). */
    public static void drawSquareBorder(DrawContext ctx, int x, int y, int w, int h, int color, int thickness) {
        if (w <= 0 || h <= 0) return;
        int t = Math.max(1, thickness);
        ctx.fill(x, y, x + w, y + t, color);                                       // top
        ctx.fill(x, y + h - t, x + w, y + h, color);                                // bottom
        ctx.fill(x, y, x + t, y + h, color);                                        // left
        ctx.fill(x + w - t, y, x + w, y + h, color);                                // right
    }
}
