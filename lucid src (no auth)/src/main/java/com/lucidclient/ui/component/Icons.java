package com.lucidclient.ui.component;

import net.minecraft.client.gui.DrawContext;

/**
 * Procedurally-drawn stroke-vector icons for Lucid Client.
 *
 * <p>Every icon is rendered as a cohesive set of <b>2&nbsp;px strokes</b>
 * with round caps and round joins — the visual language of outline icon
 * families such as Lucide / Feather. No fill-based "staircase" shapes, no
 * scattered pixels, no hardcoded cut-out colours. The result reads as crisp
 * vector art at the small sizes the ClickGUI actually uses (12, 14, 16&nbsp;px)
 * and degrades gracefully down to 12&nbsp;px.
 *
 * <p>All drawing ultimately goes through {@link DrawContext#fill} — the
 * standard 1.21.11 yarn-mapped pixel-fill API (x2/y2 are exclusive). Strokes
 * are rasterised as 2&times;2 pixel stamps walked along a Bresenham centreline
 * (axis-aligned and diagonal lines) or a parametric centreline (circles /
 * arcs / ellipses); round caps are radius-1 filled disks stamped at the
 * stroke ends so tips and corners read as little round dots rather than
 * chopped squares. Circles use the midpoint algorithm (8-way symmetry) for
 * crisp edges.
 *
 * <h3>Public surface (signatures are part of the API — do not change)</h3>
 * Helpers: {@link #drawFilledCircle}, {@link #drawCircleOutline},
 * {@link #drawThickLine}. Icons: {@link #drawSword}, {@link #drawGear},
 * {@link #drawDonut}, {@link #drawCompass}, {@link #drawSearch},
 * {@link #drawChevronDown}, {@link #drawChevronRight}, {@link #drawCheck},
 * {@link #drawClose}, {@link #drawPlus}, {@link #drawGrid},
 * {@link #drawMagnet}, {@link #drawReset}, {@link #drawSave},
 * {@link #drawZoom}, {@link #drawInfo}, {@link #drawEye},
 * {@link #drawFeather}, {@link #drawHeart}. Dispatcher:
 * {@link #drawCategoryIcon}.
 *
 * <p>Angle convention (used by strokes that take degrees): 0&deg; = right,
 * 90&deg; = up, 180&deg; = left, 270&deg; = down — standard math convention
 * with screen Y running downward (a positive {@code sin} lifts the point
 * up). A positive sweep goes counterclockwise visually; a negative sweep goes
 * clockwise.
 */
public final class Icons {
    private Icons() {}

    // ════════════════════════════════════════════════════════════════
    //  Low-level pixel primitives — midpoint-circle based for crisp edges.
    // ════════════════════════════════════════════════════════════════

    /**
     * Filled disk, radius {@code r}, via the midpoint circle algorithm filled
     * with horizontal spans using 8-way symmetry. Crisper than per-scanline
     * {@code round(sqrt())} at small radii.
     */
    public static void drawFilledCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        if (r < 0) return;
        if (r == 0) { ctx.fill(cx, cy, cx + 1, cy + 1, color); return; }
        int x = 0, y = r;
        int d = 1 - r;
        fillSymmetricSpans(ctx, cx, cy, x, y, color);
        while (x < y) {
            x++;
            if (d < 0) {
                d += 2 * x + 1;
            } else {
                y--;
                d += 2 * (x - y) + 1;
            }
            fillSymmetricSpans(ctx, cx, cy, x, y, color);
        }
    }

    /** Plot four horizontal spans for 8-way symmetry at the current (x, y). */
    private static void fillSymmetricSpans(DrawContext ctx, int cx, int cy, int x, int y, int color) {
        // Rows cy ± y, between cx ∓ x.
        ctx.fill(cx - x, cy - y, cx + x + 1, cy - y + 1, color);
        ctx.fill(cx - x, cy + y, cx + x + 1, cy + y + 1, color);
        // Rows cy ± x, between cx ∓ y.
        if (x != y) {
            ctx.fill(cx - y, cy - x, cx + y + 1, cy - x + 1, color);
            ctx.fill(cx - y, cy + x, cx + y + 1, cy + x + 1, color);
        }
    }

    /**
     * Stroked (ring) circle of outer radius {@code r} and the given
     * {@code thickness}. Built from {@code thickness} concentric 1&nbsp;px
     * midpoint outlines — crisper and more uniform than scanline annulus
     * fills, and never banded.
     */
    public static void drawCircleOutline(DrawContext ctx, int cx, int cy, int r, int thickness, int color) {
        if (r <= 0) return;
        thickness = Math.max(1, thickness);
        int inner = r - thickness + 1;
        if (inner <= 0) {
            // Thickness exceeds radius — fill the disk to complete the ring.
            drawFilledCircle(ctx, cx, cy, r, color);
            return;
        }
        for (int rr = r; rr >= inner; rr--) {
            midpointOutline(ctx, cx, cy, rr, color);
        }
    }

    /** 1&nbsp;px circle outline via the midpoint algorithm + 8-way symmetry. */
    private static void midpointOutline(DrawContext ctx, int cx, int cy, int r, int color) {
        if (r <= 0) { ctx.fill(cx, cy, cx + 1, cy + 1, color); return; }
        int x = 0, y = r;
        int d = 1 - r;
        plot8(ctx, cx, cy, x, y, color);
        while (x < y) {
            x++;
            if (d < 0) {
                d += 2 * x + 1;
            } else {
                y--;
                d += 2 * (x - y) + 1;
            }
            plot8(ctx, cx, cy, x, y, color);
        }
    }

    /** Plot the 8 symmetric 1-pixel points of a circle octant. */
    private static void plot8(DrawContext ctx, int cx, int cy, int x, int y, int color) {
        ctx.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
        ctx.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
        ctx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
        ctx.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
        if (x != y) {
            ctx.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
            ctx.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
            ctx.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
            ctx.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
        }
    }

    /**
     * Line of the given {@code thickness} between two points, rasterised as a
     * thickness&times;thickness square stamped along a Bresenham centreline,
     * with <b>round caps</b> (a radius-{@code thickness/2} filled disk) at
     * each end so the stroke tips read as rounded rather than chopped.
     */
    public static void drawThickLine(DrawContext ctx, int x1, int y1, int x2, int y2,
                                      int thickness, int color) {
        if (thickness <= 0) return;
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1, y = y1;
        int half = thickness / 2;
        int size = thickness;
        while (true) {
            ctx.fill(x - half, y - half, x - half + size, y - half + size, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
        // Round caps: fill a small disk at each endpoint so the line tips
        // round off instead of showing a hard square edge.
        if (half > 0) {
            drawFilledCircle(ctx, x1, y1, half, color);
            drawFilledCircle(ctx, x2, y2, half, color);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Stroke toolkit (2 px) — the shared vocabulary every icon below uses.
    // ════════════════════════════════════════════════════════════════

    /** 2&nbsp;px round-capped line. */
    private static void strokeLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        drawThickLine(ctx, x1, y1, x2, y2, 2, color);
    }

    /** Round dot — radius-1 filled disk (5-pixel plus), used for caps, pivots, pupils. */
    private static void strokeDot(DrawContext ctx, int cx, int cy, int color) {
        drawFilledCircle(ctx, cx, cy, 1, color);
    }

    /** 2&nbsp;px stroked circle (midpoint outlines at {@code r} and {@code r-1}). */
    private static void strokeCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        drawCircleOutline(ctx, cx, cy, r, 2, color);
    }

    /**
     * 2&nbsp;px stroked arc, swept from {@code startDeg} by {@code extentDeg}
     * (negative extent sweeps clockwise), with round caps at both ends.
     * Rasterised by stamping a 2&times;2 square at evenly spaced points along
     * the arc — dense enough ({@code ~1px} spacing) that stamps overlap into a
     * smooth, gap-free curve.
     */
    private static void strokeArc(DrawContext ctx, int cx, int cy, int r,
                                  double startDeg, double extentDeg, int color) {
        if (r <= 0) return;
        double start = Math.toRadians(startDeg);
        double end = Math.toRadians(startDeg + extentDeg);
        double arcLen = Math.abs(end - start);
        int steps = Math.max(2, (int) Math.ceil(arcLen * r));
        for (int i = 0; i <= steps; i++) {
            double a = start + (end - start) * i / steps;
            int px = cx + (int) Math.round(r * Math.cos(a));
            int py = cy - (int) Math.round(r * Math.sin(a));
            ctx.fill(px, py, px + 2, py + 2, color);
        }
        int[] s = pointOnCircle(cx, cy, r, startDeg);
        int[] e = pointOnCircle(cx, cy, r, startDeg + extentDeg);
        strokeDot(ctx, s[0], s[1], color);
        strokeDot(ctx, e[0], e[1], color);
    }

    /** 2&nbsp;px stroked ellipse outline (for the eye's lens). */
    private static void strokeEllipse(DrawContext ctx, int cx, int cy, int rw, int rh, int color) {
        if (rw <= 0 || rh <= 0) return;
        int steps = Math.max(16, (rw + rh) * 3);
        for (int i = 0; i < steps; i++) {
            double a = (i * 2 * Math.PI) / steps;
            int px = cx + (int) Math.round(rw * Math.cos(a));
            int py = cy - (int) Math.round(rh * Math.sin(a));
            ctx.fill(px, py, px + 2, py + 2, color);
        }
    }

    /**
     * 2&nbsp;px stroked <b>arc of an ellipse</b>, swept from {@code startDeg}
     * by {@code extentDeg} (CCW positive), stamping 2&times;2 squares densely
     * enough to overlap into a smooth curve. Used to build the almond/lens
     * eye shape (upper arc + lower arc meeting at the two corners).
     */
    private static void strokeEllipseArc(DrawContext ctx, int cx, int cy, int rw, int rh,
                                         double startDeg, double extentDeg, int color) {
        if (rw <= 0 || rh <= 0) return;
        double start = Math.toRadians(startDeg);
        double end = Math.toRadians(startDeg + extentDeg);
        int steps = Math.max(12, (int) Math.ceil(Math.abs(end - start) * Math.max(rw, rh)));
        for (int i = 0; i <= steps; i++) {
            double a = start + (end - start) * i / steps;
            int px = cx + (int) Math.round(rw * Math.cos(a));
            int py = cy - (int) Math.round(rh * Math.sin(a));
            ctx.fill(px, py, px + 2, py + 2, color);
        }
    }

    /** 2&nbsp;px stroked rectangle outline (x2/y2 inclusive bottom-right). */
    private static void strokeRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 < x1 || y2 < y1) return;
        ctx.fill(x1, y1, x2 + 1, y1 + 2, color);       // top
        ctx.fill(x1, y2 - 1, x2 + 1, y2 + 1, color);   // bottom
        ctx.fill(x1, y1, x1 + 2, y2 + 1, color);       // left
        ctx.fill(x2 - 1, y1, x2 + 1, y2 + 1, color);   // right
    }

    /** The pixel on a circle of radius {@code r} at the given angle (degrees). */
    private static int[] pointOnCircle(int cx, int cy, int r, double deg) {
        double a = Math.toRadians(deg);
        return new int[] {
            cx + (int) Math.round(r * Math.cos(a)),
            cy - (int) Math.round(r * Math.sin(a))
        };
    }

    /** Fill a vertical diamond (rhombus): vertices at ({@code cx},{@code top}), ({@code cx},{@code bot}), ({@code cx±half}). */
    private static void fillDiamond(DrawContext ctx, int cx, int top, int bot, int half, int color) {
        int mid = (top + bot) / 2;
        int up = Math.max(1, mid - top);
        for (int yy = top; yy <= mid; yy++) {
            int hw = (int) Math.round((double) half * (yy - top) / up);
            ctx.fill(cx - hw, yy, cx + hw + 1, yy + 1, color);
        }
        int dn = Math.max(1, bot - mid);
        for (int yy = mid + 1; yy <= bot; yy++) {
            int hw = (int) Math.round((double) half * (bot - yy) / dn);
            ctx.fill(cx - hw, yy, cx + hw + 1, yy + 1, color);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Icons — one shared 2 px stroke vocabulary, uniform insets, round
    //  caps/joins. Geometry keys off `s` so each icon scales with its box.
    // ════════════════════════════════════════════════════════════════

    // ── Sword (Combat) — diagonal blade + perpendicular quillon + handle + pommel
    public static void drawSword(DrawContext ctx, int x, int y, int s, int color) {
        // Blade tip (top-right), guard where the blade meets the crossguard.
        int tipX = x + s - 3, tipY = y + 3;
        int gX = x + s / 2 - 2, gY = y + s / 2 + 2;
        strokeLine(ctx, gX, gY, tipX, tipY, color);          // blade (round cap = pointed tip)
        // Crossguard — perpendicular stub (+s/cross length) through the guard.
        int cg = Math.max(3, s / 4 + 1);
        strokeLine(ctx, gX - cg / 2, gY - cg / 2, gX + cg / 2, gY + cg / 2, color);
        // Handle continuing away from the blade + round pommel dot.
        strokeLine(ctx, gX, gY, gX - 3, gY + 3, color);
        strokeDot(ctx, gX - 3, gY + 3, color);
    }

    // ── Gear / Settings — ring + 8 radial tooth stubs
    public static void drawGear(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int r = Math.max(3, s / 2 - 3);          // ring radius
        strokeCircle(ctx, cx, cy, r, color);    // 2 px ring
        // 8 tooth stubs straddling the ring and sticking out ~2 px.
        for (int i = 0; i < 8; i++) {
            double deg = i * 45.0;
            int[] in = pointOnCircle(cx, cy, Math.max(1, r - 1), deg);
            int[] out = pointOnCircle(cx, cy, r + 2, deg);
            strokeLine(ctx, in[0], in[1], out[0], out[1], color);
        }
    }

    // ── Donut (DonutSMP category) — clean thick ring
    public static void drawDonut(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int r = Math.max(4, s / 2 - 2);
        int thickness = Math.max(3, s / 4);
        drawCircleOutline(ctx, cx, cy, r, thickness, color);
    }

    // ── Compass (world/HUD category) — ring + vertical diamond needle + pivot
    public static void drawCompass(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int r = Math.max(3, s / 2 - 3);
        strokeCircle(ctx, cx, cy, r, color);
        int half = Math.max(2, s / 8);
        fillDiamond(ctx, cx, y + 3, y + s - 3, half, color);
        strokeDot(ctx, cx, cy, color);          // pivot
    }

    // ── Search — lens ring (upper-left) + handle (lower-right)
    public static void drawSearch(DrawContext ctx, int x, int y, int s, int color) {
        int r = Math.max(3, s * 2 / 5 - 1);
        int cx = x + r + 1, cy = y + r + 1;
        strokeCircle(ctx, cx, cy, r, color);
        int hs = (int) Math.round(r * 0.707);
        strokeLine(ctx, cx + hs, cy + hs, x + s - 2, y + s - 2, color);
    }

    // ── Zoom (magnifier + plus)
    public static void drawZoom(DrawContext ctx, int x, int y, int s, int color) {
        drawSearch(ctx, x, y, s, color);
        int r = Math.max(3, s * 2 / 5 - 1);
        int cx = x + r + 1, cy = y + r + 1;
        strokeLine(ctx, cx - 2, cy, cx + 2, cy, color);
        strokeLine(ctx, cx, cy - 2, cx, cy + 2, color);
    }

    // ── Chevron (down / right) — two strokes meeting at a rounded apex
    public static void drawChevronDown(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2;
        strokeLine(ctx, x + 3, y + 4, cx, y + s - 4, color);
        strokeLine(ctx, cx, y + s - 4, x + s - 3, y + 4, color);
    }

    public static void drawChevronRight(DrawContext ctx, int x, int y, int s, int color) {
        int cy = y + s / 2;
        strokeLine(ctx, x + 4, y + 3, x + s - 4, cy, color);
        strokeLine(ctx, x + s - 4, cy, x + 4, y + s - 3, color);
    }

    // ── Solid arrow glyphs (head + shaft) — the clearer, bolder siblings of
    //    the bare chevrons. Used as the expand/collapse indicator on both the
    //    category panel (minimize/maximize) and the per-module settings toggle
    //    (the right-click customizations). A short round-capped shaft makes
    //    them read unambiguously as arrows at small sizes.
    public static void drawArrowDown(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2;
        int tipY = y + s - 3;           // tip at the bottom
        int neckY = y + s - 6;          // where the head splits from the shaft
        // Shaft (top → neck), round caps.
        strokeLine(ctx, cx, y + 3, cx, neckY, color);
        // V head opening upward, meeting the shaft at the neck (round join).
        strokeLine(ctx, x + 3, neckY, cx, tipY, color);
        strokeLine(ctx, cx, tipY, x + s - 3, neckY, color);
    }

    public static void drawArrowRight(DrawContext ctx, int x, int y, int s, int color) {
        int cy = y + s / 2;
        int tipX = x + s - 3;           // tip at the right
        int neckX = x + s - 6;          // where the head splits from the shaft
        // Shaft (left → neck), round caps.
        strokeLine(ctx, x + 3, cy, neckX, cy, color);
        // > head opening leftward, meeting the shaft at the neck (round join).
        strokeLine(ctx, neckX, y + 3, tipX, cy, color);
        strokeLine(ctx, tipX, cy, neckX, y + s - 3, color);
    }

    // ── Checkmark — short leg down to a corner, long leg up to the top-right
    public static void drawCheck(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2;
        int cornerX = cx, cornerY = y + s - 4;
        strokeLine(ctx, x + 2, y + s / 2, cornerX, cornerY, color);
        strokeLine(ctx, cornerX, cornerY, x + s - 3, y + 3, color);
    }

    // ── Close X — two corner-to-corner diagonals
    public static void drawClose(DrawContext ctx, int x, int y, int s, int color) {
        int inset = Math.max(2, s / 6);
        strokeLine(ctx, x + inset, y + inset, x + s - inset, y + s - inset, color);
        strokeLine(ctx, x + s - inset, y + inset, x + inset, y + s - inset, color);
    }

    // ── Plus — two perpendicular strokes crossing at the centre
    public static void drawPlus(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int arm = Math.max(3, s / 3 - 1);
        strokeLine(ctx, cx - arm, cy, cx + arm, cy, color);
        strokeLine(ctx, cx, cy - arm, cx, cy + arm, color);
    }

    // ── Grid — 3×3 cells with a square border
    public static void drawGrid(DrawContext ctx, int x, int y, int s, int color) {
        int a = Math.round(s / 3.0f);
        int b = s - Math.round(s / 3.0f);
        // Inner grid lines (2 px).
        if (a > 1) ctx.fill(x + a - 1, y, x + a + 1, y + s, color);
        if (b > 1 && b < s) ctx.fill(x + b - 1, y, x + b + 1, y + s, color);
        if (a > 1) ctx.fill(x, y + a - 1, x + s, y + a + 1, color);
        if (b > 1 && b < s) ctx.fill(x, y + b - 1, x + s, y + b + 1, color);
        // Border (2 px).
        ctx.fill(x, y, x + s, y + 2, color);
        ctx.fill(x, y + s - 2, x + s, y + s, color);
        ctx.fill(x, y, x + 2, y + s, color);
        ctx.fill(x + s - 2, y, x + s, y + s, color);
    }

    // ── Magnet (snap) — bottom U-arc + two prongs + pole caps
    public static void drawMagnet(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int r = Math.max(3, s / 2 - 2);
        strokeArc(ctx, cx, cy, r, 180, 180, color);     // bottom semicircle (left→down→right)
        int top = y + Math.max(2, s / 8);
        // Prongs up to the top.
        strokeLine(ctx, cx - r, cy, cx - r, top, color);
        strokeLine(ctx, cx + r, cy, cx + r, top, color);
        // Pole caps (small horizontal stubs) at each prong tip.
        strokeLine(ctx, cx - r - 1, top, cx - r + 1, top, color);
        strokeLine(ctx, cx + r - 1, top, cx + r + 1, top, color);
    }

    // ── Reset (circular arrow) — ~270° arc + arrowhead
    public static void drawReset(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int r = Math.max(3, s / 2 - 2);
        // Arc from upper-left (135°) counterclockwise around to upper-right (45°),
        // leaving the gap across the top for the arrowhead.
        strokeArc(ctx, cx, cy, r, 135, 270, color);
        int[] e = pointOnCircle(cx, cy, r, 45);          // arrow tip (upper-right)
        int barb = Math.max(2, s / 6);
        // Two short barbs opening back/down-right from the tip → arrowhead.
        strokeLine(ctx, e[0], e[1], e[0], e[1] + barb, color);
        strokeLine(ctx, e[0], e[1], e[0] + barb, e[1], color);
    }

    // ── Save (floppy) — body outline + label outline (theme-agnostic: no cut-outs)
    public static void drawSave(DrawContext ctx, int x, int y, int s, int color) {
        strokeRect(ctx, x + 2, y + 2, x + s - 3, y + s - 3, color);        // body
        strokeRect(ctx, x + 4, y + s / 2, x + s - 5, y + s - 4, color);   // label slot
    }

    // ── Info — ring + tittle + stem (a clean lower-case "i")
    public static void drawInfo(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int r = Math.max(3, s / 2 - 2);
        strokeCircle(ctx, cx, cy, r, color);
        strokeDot(ctx, cx, cy - 3, color);               // tittle
        ctx.fill(cx - 1, cy - 1, cx + 1, cy + 4, color);  // stem (2 px wide)
    }

    // ── Eye (Render category + module-row settings toggle) — an almond/lens
    //    eye: upper + lower ellipse arcs meeting at two rounded corner points,
    //    with a filled pupil clearly smaller than the lens so the outline
    //    reads around it. The almond shape (vs. a full flat oval) is the
    //    canonical eye silhouette and stays legible down to ~10px.
    public static void drawEye(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int rw = Math.max(3, s / 2 - 1);          // lens half-width
        int rh = Math.max(2, (s + 1) / 3);         // lens half-height (taller → reads as eye, not slit)
        // Upper arc (over the top): right corner → left corner.
        strokeEllipseArc(ctx, cx, cy, rw, rh, 0, 180, color);
        // Lower arc (under the bottom): left corner → right corner.
        strokeEllipseArc(ctx, cx, cy, rw, rh, 180, 180, color);
        // Round the two corners where the arcs meet (round joins).
        strokeDot(ctx, cx - rw, cy, color);
        strokeDot(ctx, cx + rw, cy, color);
        // Pupil — filled disk clearly smaller than the lens.
        drawFilledCircle(ctx, cx, cy, Math.max(1, s / 6), color);
    }

    // ── Eye-off — the "hidden eye" idiom used as the collapsed-state
    //    companion to {@link #drawEye}'s visible-state. Renders the same
    //    almond eye (lens + pupil) plus a 2&nbsp;px round-capped diagonal
    //    slash running top-right → bottom-left across the lens, matching
    //    the surrounding 2&nbsp;px stroke-vector icon set (consistent caps/joins).
    //    Used by the ClickGUI module-row settings toggle: open eye when a
    //    module's customizations are expanded, this slashed eye when collapsed.
    public static void drawEyeOff(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2, cy = y + s / 2;
        int rw = Math.max(3, s / 2 - 1);
        int rh = Math.max(2, (s + 1) / 3);
        strokeEllipseArc(ctx, cx, cy, rw, rh, 0, 180, color);
        strokeEllipseArc(ctx, cx, cy, rw, rh, 180, 180, color);
        strokeDot(ctx, cx - rw, cy, color);
        strokeDot(ctx, cx + rw, cy, color);
        drawFilledCircle(ctx, cx, cy, Math.max(1, s / 6), color);
        // Diagonal slash (top-right → bottom-left) crossing the whole icon.
        strokeLine(ctx, x + s - 1, y + 1, x + 1, y + s - 1, color);
    }

    // ── Feather (Movement category) — diagonal shaft + 3 barbs on the lower side
    public static void drawFeather(DrawContext ctx, int x, int y, int s, int color) {
        int ax = x + 3, ay = y + s - 3;       // shaft base (bottom-left)
        int bx = x + s - 3, by = y + 3;       // shaft tip (top-right)
        strokeLine(ctx, ax, ay, bx, by, color);   // shaft (round cap = pointed tip)
        int barb = Math.max(2, s / 6);
        for (int i = 1; i <= 3; i++) {
            double t = i / 5.0;
            int px = ax + (int) Math.round((bx - ax) * t);
            int py = ay + (int) Math.round((by - ay) * t);
            strokeLine(ctx, px, py, px - barb, py - barb, color);   // barb up-left
        }
    }

    // ── Heart (Player category) — outlined: two lobe arcs + two cone strokes to a point
    public static void drawHeart(DrawContext ctx, int x, int y, int s, int color) {
        int cx = x + s / 2;
        int r = Math.max(2, s / 5);
        int lobeY = y + r + 1;
        int tipY = y + s - 4;
        // Left lobe — arc from the top dip (~30°) over the top down to lower-left (~225°).
        strokeArc(ctx, cx - r, lobeY, r, 30, 195, color);
        // Right lobe — mirror, from the dip (~150°) over the top down to lower-right (~315°).
        strokeArc(ctx, cx + r, lobeY, r, 150, -195, color);
        // Cone sides down to the bottom point.
        int[] le = pointOnCircle(cx - r, lobeY, r, 225);
        int[] re = pointOnCircle(cx + r, lobeY, r, 315);
        strokeLine(ctx, le[0], le[1], cx, tipY, color);
        strokeLine(ctx, re[0], re[1], cx, tipY, color);
    }

    // ════════════════════════════════════════════════════════════════
    //  Category dispatcher
    // ════════════════════════════════════════════════════════════════

    /** Draw a category icon by name. Falls back to {@link #drawInfo}. */
    public static void drawCategoryIcon(DrawContext ctx, String name, int x, int y, int s, int color) {
        switch (name) {
            case "Sword": drawSword(ctx, x, y, s, color); break;
            case "Settings": drawGear(ctx, x, y, s, color); break;
            case "Donut": drawDonut(ctx, x, y, s, color); break;
            case "Compass": drawCompass(ctx, x, y, s, color); break;
            case "Eye": drawEye(ctx, x, y, s, color); break;
            case "Feather": drawFeather(ctx, x, y, s, color); break;
            case "Heart": drawHeart(ctx, x, y, s, color); break;
            default: drawInfo(ctx, x, y, s, color);
        }
    }
}
