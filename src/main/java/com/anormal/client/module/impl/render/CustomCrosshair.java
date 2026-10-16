package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Fully customisable crosshair that replaces the vanilla one.
 *
 * <p>Ten shapes, per-shape size/thickness/gap, optional centre dot, outline,
 * rotation, opacity, rainbow, a dynamic colour that follows your attack
 * cooldown, and free X/Y offsets. The vanilla crosshair is suppressed by
 * {@code InGameHudMixin} while this module is on.</p>
 */
public class CustomCrosshair extends Module {

    // ---- Shape ----
    public final ModeSetting shape = new ModeSetting("Shape", "Crosshair shape", "Cross",
            "Cross", "X", "Dot", "Circle", "Square", "Diamond", "Triangle", "Arrow", "Cross + Dot", "T-Shape");
    public final NumberSetting size = new NumberSetting("Size", "Length of the arms / shape radius", 6.0, 1.0, 30.0, 1.0);
    public final NumberSetting thickness = new NumberSetting("Thickness", "Line thickness in pixels", 2.0, 1.0, 8.0, 1.0);
    public final NumberSetting gap = new NumberSetting("Gap", "Empty space between the centre and the arms", 3.0, 0.0, 15.0, 1.0);

    // ---- Extras ----
    public final BooleanSetting dot = new BooleanSetting("Centre Dot", "Draw a dot in the middle", false);
    public final NumberSetting dotSize = new NumberSetting("Dot Size", "Radius of the centre dot", 1.0, 1.0, 6.0, 1.0);
    public final NumberSetting rotation = new NumberSetting("Rotation", "Rotate the shape (degrees)", 0.0, 0.0, 360.0, 5.0);

    // ---- Layout ----
    public final NumberSetting scale = new NumberSetting("Scale", "Overall size multiplier", 1.0, 0.25, 3.0, 0.05);
    public final NumberSetting offsetX = new NumberSetting("Offset X", "Move the crosshair horizontally", 0.0, -300.0, 300.0, 1.0);
    public final NumberSetting offsetY = new NumberSetting("Offset Y", "Move the crosshair vertically", 0.0, -300.0, 300.0, 1.0);

    // ---- Colour ----
    public final ColorSetting color = new ColorSetting("Color", "Crosshair colour", ColorUtils.rgba(255, 255, 255, 255));
    public final NumberSetting opacity = new NumberSetting("Opacity", "Colour opacity (0-255)", 255.0, 0.0, 255.0, 5.0);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a dark outline for contrast", true);
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", "Outline colour", ColorUtils.rgba(0, 0, 0, 180));

    // ---- Dynamic ----
    public final BooleanSetting dynamicColor = new BooleanSetting("Attack Cooldown Color", "Tint the crosshair while the attack cooldown is recharging", true);
    public final ColorSetting cooldownColor = new ColorSetting("Cooldown Color", "Colour used while recharging", ColorUtils.rgba(255, 70, 70, 255));
    public final BooleanSetting rainbow = new BooleanSetting("Rainbow", "Cycle the colour over time", false);
    public final NumberSetting rainbowSpeed = new NumberSetting("Rainbow Speed", "How fast the rainbow cycles", 2.0, 0.5, 10.0, 0.5);
    public final BooleanSetting pulseOnAttack = new BooleanSetting("Pulse On Attack", "Briefly enlarge the crosshair when you attack", true);
    public final NumberSetting pulseAmount = new NumberSetting("Pulse Amount", "How much the pulse enlarges the crosshair", 1.5, 1.0, 3.0, 0.1);

    // ---- Behaviour ----
    public final BooleanSetting hideVanilla = new BooleanSetting("Hide Vanilla", "Hide the vanilla crosshair", true);
    public final BooleanSetting hideInScreens = new BooleanSetting("Hide In Screens", "Do not draw while a GUI is open", true);
    public final BooleanSetting hideInThirdPerson = new BooleanSetting("Hide In Third Person", "Do not draw in third-person view", true);

    private float pulse = 0f;
    private boolean attackWasDown = false;

    public CustomCrosshair() {
        super("CustomCrosshair", "Replaces the vanilla crosshair with a fully customisable one", Category.RENDER);
        addSetting(shape);
        addSetting(size);
        addSetting(thickness);
        addSetting(gap);
        addSetting(dot);
        addSetting(dotSize);
        addSetting(rotation);
        addSetting(scale);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(color);
        addSetting(opacity);
        addSetting(outline);
        addSetting(outlineColor);
        addSetting(dynamicColor);
        addSetting(cooldownColor);
        addSetting(rainbow);
        addSetting(rainbowSpeed);
        addSetting(pulseOnAttack);
        addSetting(pulseAmount);
        addSetting(hideVanilla);
        addSetting(hideInScreens);
        addSetting(hideInThirdPerson);
    }

    @Override
    public void onDisable() {
        pulse = 0f;
    }

    @Override
    public void onTick() {
        // Decay the attack pulse back to normal.
        if (pulse > 0f) pulse = Math.max(0f, pulse - 0.08f);

        // Detect the rising edge of the attack key so the crosshair can pop.
        boolean down = mc.options != null && mc.options.attackKey.isPressed();
        if (down && !attackWasDown) onAttack();
        attackWasDown = down;
    }

    /** Called by the mixin so a swing makes the crosshair pop. */
    public void onAttack() {
        if (pulseOnAttack.isEnabled()) pulse = 1f;
    }

    /** True when the vanilla crosshair should be suppressed. */
    public boolean shouldHideVanilla() {
        return isEnabled() && hideVanilla.isEnabled();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        if (hideInScreens.isEnabled() && mc.currentScreen != null) return;
        if (hideInThirdPerson.isEnabled() && !mc.options.getPerspective().isFirstPerson()) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        double sc = scale.getValue() * (1.0 + pulse * (pulseAmount.getValue() - 1.0));
        int cx = (int) (sw / 2.0 + offsetX.getValue());
        int cy = (int) (sh / 2.0 + offsetY.getValue());

        int base = currentColor();
        int out = outlineColor.getValue();

        String mode = shape.getValue();

        if (mode.equals("Dot")) {
            plotCircle(context, cx, cy, Math.max(1, (int) Math.round(size.getValue() * sc)), true, base, out);
        } else if (mode.equals("Circle")) {
            plotCircle(context, cx, cy, Math.max(2, (int) Math.round(size.getValue() * sc)), false, base, out);
        } else if (mode.equals("Square")) {
            int r = Math.max(2, (int) Math.round(size.getValue() * sc));
            drawSquare(context, cx, cy, r, base, out);
        } else if (mode.equals("Diamond")) {
            int r = Math.max(2, (int) Math.round(size.getValue() * sc));
            drawDiamond(context, cx, cy, r, base, out);
        } else if (mode.equals("Triangle")) {
            int r = Math.max(2, (int) Math.round(size.getValue() * sc));
            drawTriangle(context, cx, cy, r, base, out);
        } else if (mode.equals("Arrow")) {
            drawArrow(context, cx, cy, sc, base, out);
        } else if (mode.equals("T-Shape")) {
            drawTShape(context, cx, cy, sc, base, out);
        } else if (mode.equals("X")) {
            drawCross(context, cx, cy, sc, base, out, true);
        } else { // Cross / Cross + Dot
            drawCross(context, cx, cy, sc, base, out, false);
        }

        if (dot.isEnabled() || mode.equals("Cross + Dot")) {
            plotCircle(context, cx, cy, Math.max(1, (int) Math.round(dotSize.getValue() * sc)), true, base, out);
        }
    }

    // ------------------------------------------------------------------
    //  Colour helpers
    // ------------------------------------------------------------------

    private int currentColor() {
        int c;
        if (rainbow.isEnabled()) {
            c = ColorUtils.rainbow((int) (System.currentTimeMillis() / (long) (110 - rainbowSpeed.getValue() * 10)), 0.8f, 1.0f);
        } else if (dynamicColor.isEnabled() && isCoolingDown()) {
            c = cooldownColor.getValue();
        } else {
            c = color.getValue();
        }
        int a = opacity.getValue().intValue();
        return (c & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    private boolean isCoolingDown() {
        try {
            return mc.player != null && mc.player.getAttackCooldownProgress(0.0f) < 1.0f;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Shape drawing (we only have axis-aligned fills, so shapes are
    //  rasterised by hand)
    // ------------------------------------------------------------------

    /** Draws the four arms of a cross (or an X when {@code diagonal}). */
    private void drawCross(DrawContext context, int cx, int cy, double sc, int col, int outlineCol, boolean diagonal) {
        int len = (int) Math.round(size.getValue() * sc);
        int gapPx = (int) Math.round(gap.getValue() * sc);
        int th = (int) Math.round(thickness.getValue() * sc);
        double rot = Math.toRadians(rotation.getValue());

        double[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        if (diagonal) dirs = new double[][]{{-0.7071, -0.7071}, {0.7071, 0.7071}, {-0.7071, 0.7071}, {0.7071, -0.7071}};

        for (double[] d : dirs) {
            double rx = d[0] * Math.cos(rot) - d[1] * Math.sin(rot);
            double ry = d[0] * Math.sin(rot) + d[1] * Math.cos(rot);
            int x1 = (int) Math.round(cx + rx * gapPx);
            int y1 = (int) Math.round(cy + ry * gapPx);
            int x2 = (int) Math.round(cx + rx * (gapPx + len));
            int y2 = (int) Math.round(cy + ry * (gapPx + len));
            thickLine(context, x1, y1, x2, y2, th, col, outlineCol);
        }
    }

    private void drawArrow(DrawContext context, int cx, int cy, double sc, int col, int outlineCol) {
        int len = (int) Math.round(size.getValue() * sc);
        int gapPx = (int) Math.round(gap.getValue() * sc);
        int th = (int) Math.round(thickness.getValue() * sc);

        // Vertical arm pointing up + two diagonal barbs.
        thickLine(context, cx, cy - gapPx, cx, cy - gapPx - len, th, col, outlineCol);
        thickLine(context, cx, cy - gapPx - len, cx - len / 2, cy - gapPx - len + len / 2, th, col, outlineCol);
        thickLine(context, cx, cy - gapPx - len, cx + len / 2, cy - gapPx - len + len / 2, th, col, outlineCol);
        // Short bottom tick so the centre stays readable.
        thickLine(context, cx, cy + gapPx, cx, cy + gapPx + Math.max(1, len / 3), th, col, outlineCol);
    }

    private void drawTShape(DrawContext context, int cx, int cy, double sc, int col, int outlineCol) {
        int len = (int) Math.round(size.getValue() * sc);
        int gapPx = (int) Math.round(gap.getValue() * sc);
        int th = (int) Math.round(thickness.getValue() * sc);
        thickLine(context, cx - len, cy - gapPx, cx + len, cy - gapPx, th, col, outlineCol);
        thickLine(context, cx, cy - gapPx, cx, cy - gapPx - len, th, col, outlineCol);
        thickLine(context, cx, cy + gapPx, cx, cy + gapPx + len / 2, th, col, outlineCol);
    }

    private void drawSquare(DrawContext context, int cx, int cy, int r, int col, int outlineCol) {
        int th = (int) Math.round(thickness.getValue());
        outlineRect(context, cx - r, cy - r, cx + r, cy + r, th, col, outlineCol);
    }

    private void drawDiamond(DrawContext context, int cx, int cy, int r, int col, int outlineCol) {
        int th = Math.max(1, (int) Math.round(thickness.getValue()));
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            int y = cy + i;
            if (Math.abs(i) >= r - th || half <= th) {
                thickLine(context, cx - half, y, cx + half, y, th, col, outlineCol);
            } else {
                thickLine(context, cx - half, y, cx - half + th, y, th, col, outlineCol);
                thickLine(context, cx + half - th, y, cx + half, y, th, col, outlineCol);
            }
        }
    }

    private void drawTriangle(DrawContext context, int cx, int cy, int r, int col, int outlineCol) {
        int th = Math.max(1, (int) Math.round(thickness.getValue()));
        int apexY = cy - r;
        int baseY = cy + r;
        // Two slanted sides.
        for (int i = 0; i <= r * 2; i++) {
            int y = apexY + i;
            int half = i / 2;
            thickLine(context, cx - half, y, cx - half + th, y, th, col, outlineCol);
            thickLine(context, cx + half - th, y, cx + half, y, th, col, outlineCol);
        }
        thickLine(context, cx - r / 2, baseY, cx + r / 2, baseY, th, col, outlineCol);
    }

    private void outlineRect(DrawContext context, int x1, int y1, int x2, int y2, int th, int col, int outlineCol) {
        if (outline.isEnabled()) {
            RenderUtils.fill(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, outlineCol);
        }
        RenderUtils.fill(context, x1, y1, x2, y2, col);
    }

    /** A line with an optional 1px outline, drawn as a run of square fills. */
    private void thickLine(DrawContext context, int x1, int y1, int x2, int y2, int th, int col, int outlineCol) {
        if (outline.isEnabled()) {
            RenderUtils.drawLine(context, x1, y1, x2, y2, outlineCol, th + 2f);
        }
        RenderUtils.drawLine(context, x1, y1, x2, y2, col, th);
    }

    /** Filled ({@code solid}) or outlined circle rasterised with scanlines. */
    private void plotCircle(DrawContext context, int cx, int cy, int r, boolean solid, int col, int outlineCol) {
        for (int i = -r; i <= r; i++) {
            int half = (int) Math.floor(Math.sqrt((double) r * r - (double) i * i));
            int y = cy + i;
            if (solid) {
                if (outline.isEnabled()) RenderUtils.fill(context, cx - half - 1, y, cx + half + 2, y + 1, outlineCol);
                RenderUtils.fill(context, cx - half, y, cx + half + 1, y + 1, col);
            } else {
                int th = Math.max(1, (int) Math.round(thickness.getValue()));
                if (outline.isEnabled()) {
                    RenderUtils.fill(context, cx - half - 1, y, cx - half + th + 1, y + 1, outlineCol);
                    RenderUtils.fill(context, cx + half - th, y, cx + half + 2, y + 1, outlineCol);
                }
                RenderUtils.fill(context, cx - half, y, cx - half + th, y + 1, col);
                RenderUtils.fill(context, cx + half - th + 1, y, cx + half + 1, y + 1, col);
            }
        }
    }
}
