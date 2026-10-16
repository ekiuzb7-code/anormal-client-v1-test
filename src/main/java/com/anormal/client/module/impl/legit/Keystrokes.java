package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * On-screen keystrokes overlay with five completely different visual styles.
 *
 * <p>Pick a style with the {@code Type} setting:</p>
 * <ol>
 *   <li><b>Classic</b> - the original flat theme-coloured boxes.</li>
 *   <li><b>Dark Glass</b> - navy translucent panels with a cyan accent.</li>
 *   <li><b>Neon</b> - dark panels, per-key neon colours, gradient bar and FPS.</li>
 *   <li><b>Minimal</b> - light boxes with dark text, very understated.</li>
 *   <li><b>Glow</b> - dark keys that light up with a halo while pressed.</li>
 * </ol>
 */
public class Keystrokes extends Module {

    public final ModeSetting type = new ModeSetting("Type", "Visual style", "Classic",
            "Classic", "Dark Glass", "Neon", "Minimal", "Glow");
    public final NumberSetting posX = new NumberSetting("Pos X", "X position on screen", 20.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y position on screen", 120.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Overall size multiplier", 1.0, 0.5, 2.5, 0.05);
    public final NumberSetting gap = new NumberSetting("Gap", "Space between keys", 2.0, 0.0, 8.0, 1.0);
    public final NumberSetting cornerRadius = new NumberSetting("Corner Radius", "Rounded corner size (0 = square)", 0.0, 0.0, 4.0, 1.0);
    public final NumberSetting alpha = new NumberSetting("Background Opacity", "Panel opacity (0-255)", 220.0, 0.0, 255.0, 5.0);

    public final BooleanSetting showCps = new BooleanSetting("Show CPS", "Clicks per second under LMB / RMB", true);
    public final BooleanSetting showSpace = new BooleanSetting("Show Spacebar", "Spacebar row", true);
    public final BooleanSetting showLmbRmb = new BooleanSetting("Show Clicks", "LMB and RMB boxes", true);
    public final BooleanSetting arrows = new BooleanSetting("Arrow Keys", "Use arrow glyphs instead of W A S D", false);
    public final BooleanSetting showFps = new BooleanSetting("Show FPS", "Extra FPS row underneath", false);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a border around every key", true);
    public final BooleanSetting shadow = new BooleanSetting("Shadow", "Drop shadow behind keys", false);

    public final ColorSetting pressedColor = new ColorSetting("Pressed Color", "Accent used by Classic and Minimal", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting backgroundColor = new ColorSetting("Background", "Panel colour (Classic)", ColorUtils.rgba(18, 20, 26, 220));
    public final ColorSetting textColor = new ColorSetting("Text Color", "Label colour (Classic)", ColorUtils.rgba(235, 235, 240, 255));

    private final List<Long> leftClicks = new ArrayList<>();
    private final List<Long> rightClicks = new ArrayList<>();
    private boolean leftWasPressed = false;
    private boolean rightWasPressed = false;

    public Keystrokes() {
        super("Keystrokes", "Displays customizable keyboard and mouse keystrokes with CPS on HUD", Category.LEGIT);
        addSetting(type);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(gap);
        addSetting(cornerRadius);
        addSetting(alpha);
        addSetting(showCps);
        addSetting(showSpace);
        addSetting(showLmbRmb);
        addSetting(arrows);
        addSetting(showFps);
        addSetting(outline);
        addSetting(shadow);
        addSetting(pressedColor);
        addSetting(backgroundColor);
        addSetting(textColor);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        long now = System.currentTimeMillis();

        boolean leftDown = mc.options.attackKey.isPressed();
        if (leftDown && !leftWasPressed) leftClicks.add(now);
        leftWasPressed = leftDown;

        boolean rightDown = mc.options.useKey.isPressed();
        if (rightDown && !rightWasPressed) rightClicks.add(now);
        rightWasPressed = rightDown;

        leftClicks.removeIf(t -> now - t > 1000);
        rightClicks.removeIf(t -> now - t > 1000);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null || mc.options == null) return;

        int baseX = posX.getValue().intValue();
        int baseY = posY.getValue().intValue();
        double sc = scale.getValue();
        int size = (int) (20 * sc);
        int g = (int) (gap.getValue() * sc);

        boolean w = mc.options.forwardKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();

        String[] labels = arrows.isEnabled()
                ? new String[]{"▲", "◀", "▼", "▶"}
                : new String[]{"W", "A", "S", "D"};

        // W / up
        drawKey(context, baseX + size + g, baseY, size, size, labels[0], null, w, 0);
        // A S D
        drawKey(context, baseX, baseY + size + g, size, size, labels[1], null, a, 1);
        drawKey(context, baseX + size + g, baseY + size + g, size, size, labels[2], null, s, 2);
        drawKey(context, baseX + (size + g) * 2, baseY + size + g, size, size, labels[3], null, d, 3);

        int currentY = baseY + (size + g) * 2;
        int fullWidth = size * 3 + g * 2;

        if (showLmbRmb.isEnabled()) {
            boolean lmb = mc.options.attackKey.isPressed();
            boolean rmb = mc.options.useKey.isPressed();
            int mouseWidth = (fullWidth - g) / 2;
            int mouseHeight = (int) (22 * sc);

            String lmbSub = showCps.isEnabled() ? leftClicks.size() + " CPS" : null;
            String rmbSub = showCps.isEnabled() ? rightClicks.size() + " CPS" : null;

            drawKey(context, baseX, currentY, mouseWidth, mouseHeight, "LMB", lmbSub, lmb, 4);
            drawKey(context, baseX + mouseWidth + g, currentY, mouseWidth, mouseHeight, "RMB", rmbSub, rmb, 5);
            currentY += mouseHeight + g;
        }

        // Neon style gets a gradient accent bar under the mouse keys.
        if (type.is("Neon")) {
            int barHeight = (int) (4 * sc);
            drawGradientBar(context, baseX, currentY, fullWidth, barHeight);
            currentY += barHeight + g;
        }

        if (showSpace.isEnabled()) {
            boolean space = mc.options.jumpKey.isPressed();
            int spaceHeight = (int) (12 * sc);
            drawKey(context, baseX, currentY, fullWidth, spaceHeight, "—", null, space, 6);
            currentY += spaceHeight + g;
        }

        if (showFps.isEnabled()) {
            int fpsHeight = (int) (13 * sc);
            drawFpsRow(context, baseX, currentY, fullWidth, fpsHeight);
        }
    }

    // ------------------------------------------------------------------
    //  Key rendering
    // ------------------------------------------------------------------

    /** @param index 0-3 = WASD, 4-5 = LMB/RMB, 6 = space */
    private void drawKey(DrawContext context, int x, int y, int w, int h,
                         String label, String subText, boolean pressed, int index) {
        Palette p = palette(index, pressed);

        if (shadow.isEnabled()) {
            RenderUtils.fill(context, x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
        }
        if (type.is("Glow") && pressed) {
            // Soft halo behind the key.
            RenderUtils.fill(context, x - 2, y - 2, x + w + 2, y + h + 2, withAlpha(p.border, 70));
        }

        RenderUtils.fill(context, x, y, x + w, y + h, p.background);
        roundCorners(context, x, y, w, h);
        if (outline.isEnabled()) {
            RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, p.border);
        }

        if (subText != null) {
            int textW = mc.textRenderer.getWidth(label);
            RenderUtils.drawText(context, mc.textRenderer, label, x + (w - textW) / 2, y + 3, p.text, true);
            int subW = mc.textRenderer.getWidth(subText);
            RenderUtils.drawText(context, mc.textRenderer, subText, x + (w - subW) / 2, y + h - 10, p.subText, true);
        } else {
            int textW = mc.textRenderer.getWidth(label);
            RenderUtils.drawText(context, mc.textRenderer, label, x + (w - textW) / 2, y + (h - 8) / 2, p.text, true);
        }
    }

    private void drawGradientBar(DrawContext context, int x, int y, int w, int h) {
        int c1 = 0xFF7C3AED;
        int c2 = 0xFF22D3EE;
        int segments = Math.max(1, w / 2);
        for (int i = 0; i < segments; i++) {
            int sx = x + i * w / segments;
            int ex = x + (i + 1) * w / segments;
            int col = ColorUtils.interpolateColor(c1, c2, i / (float) segments);
            RenderUtils.fill(context, sx, y, Math.max(sx + 1, ex), y + h, col);
        }
    }

    private void drawFpsRow(DrawContext context, int x, int y, int w, int h) {
        int fps = mc.getCurrentFps();
        String text = fps + " FPS";
        int bg = type.is("Minimal") ? 0xFFE5E7EB : 0xE00B0F1A;
        int fg = type.is("Minimal") ? 0xFF111827 : (fps >= 120 ? 0xFF55FF55 : fps >= 60 ? 0xFFFFAA00 : 0xFFFF5555);

        RenderUtils.fill(context, x, y, x + w, y + h, bg);
        roundCorners(context, x, y, w, h);
        if (outline.isEnabled()) RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, 0x6022D3EE);
        int tw = mc.textRenderer.getWidth(text);
        RenderUtils.drawText(context, mc.textRenderer, text, x + (w - tw) / 2, y + (h - 8) / 2, fg, true);
    }

    /** Cheap rounded corners by knocking out the outer corner pixels. */
    private void roundCorners(DrawContext context, int x, int y, int w, int h) {
        int r = cornerRadius.getValue().intValue();
        if (r <= 0) return;
        int clear = 0x00000000;
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            RenderUtils.fill(context, x, y + i, x + inset, y + i + 1, clear);
            RenderUtils.fill(context, x + w - inset, y + i, x + w, y + i + 1, clear);
            RenderUtils.fill(context, x, y + h - i - 1, x + inset, y + h - i, clear);
            RenderUtils.fill(context, x + w - inset, y + h - i - 1, x + w, y + h - i, clear);
        }
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ------------------------------------------------------------------
    //  Palettes - one per visual style
    // ------------------------------------------------------------------

    private static final class Palette {
        int background;
        int border;
        int text;
        int subText;
    }

    private Palette palette(int index, boolean pressed) {
        Palette p = new Palette();
        int opacity = alpha.getValue().intValue();

        if (type.is("Dark Glass")) {
            p.background = withAlpha(pressed ? 0xFF22D3EE : 0xFF0E1526, pressed ? 235 : opacity);
            p.border = withAlpha(0xFF22D3EE, pressed ? 255 : 140);
            p.text = pressed ? 0xFF06121F : 0xFFE6F6FF;
            p.subText = withAlpha(0xFF7DD3FC, 220);
        } else if (type.is("Neon")) {
            int accent = neonAccent(index);
            p.background = withAlpha(pressed ? accent : 0xFF140B24, pressed ? 230 : opacity);
            p.border = withAlpha(accent, pressed ? 255 : 170);
            p.text = pressed ? 0xFFFFFFFF : accent;
            p.subText = withAlpha(accent, 200);
        } else if (type.is("Minimal")) {
            p.background = pressed ? 0xFFB6BDC8 : 0xFFE9EBEF;
            p.border = 0xFFC7CBD3;
            p.text = 0xFF111827;
            p.subText = 0xFF4B5563;
        } else if (type.is("Glow")) {
            int accent = pressed ? pressedColor.getValue() : 0xFF3B4A63;
            p.background = withAlpha(pressed ? 0xFF11161F : 0xFF0B0F1A, opacity);
            p.border = withAlpha(accent, pressed ? 255 : 120);
            p.text = pressed ? withAlpha(accent, 255) : 0xFFB9C6DB;
            p.subText = withAlpha(accent, 190);
        } else {
            // Classic
            p.background = pressed ? pressedColor.getValue() : withAlpha(backgroundColor.getValue(), opacity);
            p.border = ThemeManager.getBorderColor();
            p.text = pressed ? 0xFFFFFFFF : textColor.getValue();
            p.subText = 0xFFAAAAAA;
        }
        return p;
    }

    /** Distinct neon hue per key so the row reads at a glance. */
    private static int neonAccent(int index) {
        return switch (index) {
            case 0 -> 0xFFA855F7; // W   - purple
            case 1 -> 0xFF8B5CF6; // A   - violet
            case 2 -> 0xFF3B82F6; // S   - blue
            case 3 -> 0xFF22D3EE; // D   - cyan
            case 4 -> 0xFF38BDF8; // LMB - sky
            case 5 -> 0xFF34D399; // RMB - green
            default -> 0xFFF59E0B; // space - amber
        };
    }
}
