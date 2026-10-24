package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A compact color picker — swatch + hex label.
 * Click the control to expand a preset palette; click a swatch to select the color
 * and collapse.
 *
 * Scissor clipping uses {@link DrawContext#enableScissor(int, int, int, int)} /
 * {@link DrawContext#disableScissor()} so the palette doesn't bleed outside the
 * panel during the height animation.
 */
public class ColorPicker extends Component {
    private int color;
    private boolean expanded;
    private float expandProgress = 0f;
    private Runnable onChange;
    // Preset swatches (minimal palette) — matches ColorSetting presets (first 8)
    private static final int[] PRESETS = {
        0xFF111111, 0xFFFFFFFF, 0xFFE6E6E6, 0xFF666666,
        0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF3B82F6
    };

    public ColorPicker(int x, int y, int w, int h, int initial, Runnable onChange) {
        super(x, y, w, h);
        this.color = initial;
        this.onChange = onChange;
    }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        float target = expanded ? 1f : 0f;
        expandProgress += (target - expandProgress) * Math.min(1f, (1f / 160f) * dtMs);
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        int bg = Colors.CARD;
        int border = Colors.BORDER;
        RenderUtils.drawRoundedRect(ctx, x, y, width, height, LucidTheme.RADIUS_BUTTON, bg);
        RenderUtils.drawRoundedRectBorder(ctx, x, y, width, height, LucidTheme.RADIUS_BUTTON, border, 1);

        // Swatch
        int sw = height - 8;
        int sx = x + 4, sy = y + 4;
        RenderUtils.drawRoundedRect(ctx, sx, sy, sw, sw, LucidTheme.RADIUS_SMALL, color);
        RenderUtils.drawRoundedRectBorder(ctx, sx, sy, sw, sw, LucidTheme.RADIUS_SMALL, Colors.BORDER, 1);

        // Hex label
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        int labelY = RenderUtils.centerTextY(tr, y, height);
        RenderUtils.drawText(ctx, tr, hex, sx + sw + 8, labelY, Colors.TEXT_PRIMARY, false);

        // Chevron
        Icons.drawChevronDown(ctx, x + width - 16, y + (height - 12) / 2, 12, Colors.TEXT_SECONDARY);

        // Expanded palette
        if (expandProgress > 0.01f) {
            int panelH = (int) (38 * expandProgress);
            int py = y + height + 2;
            RenderUtils.drawSoftShadow(ctx, x, py, width, panelH, LucidTheme.RADIUS_BUTTON, 4, 36);
            RenderUtils.drawRoundedRect(ctx, x, py, width, panelH, LucidTheme.RADIUS_BUTTON, Colors.CARD);
            RenderUtils.drawRoundedRectBorder(ctx, x, py, width, panelH, LucidTheme.RADIUS_BUTTON, Colors.BORDER, 1);

            // Clip to the full palette height so swatches don't bleed during animation.
            int fullPanelH = 38;
            ctx.enableScissor(x, py, x + width, py + fullPanelH);
            int cell = 18;
            int gap = 4;
            for (int i = 0; i < PRESETS.length; i++) {
                int row = i / 4;
                int col = i % 4;
                int cx = x + 8 + col * (cell + gap);
                int cy = py + 8 + row * (cell + gap);
                RenderUtils.drawRoundedRect(ctx, cx, cy, cell, cell, LucidTheme.RADIUS_SMALL, PRESETS[i]);
                RenderUtils.drawRoundedRectBorder(ctx, cx, cy, cell, cell, LucidTheme.RADIUS_SMALL,
                    PRESETS[i] == color ? Colors.ACCENT : Colors.BORDER, 1);
            }
            ctx.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !enabled) return false;
        if (button != 0) return false;
        if (contains(mx, my)) {
            expanded = !expanded;
            return true;
        }
        if (expanded) {
            int py = y + height + 2;
            int cell = 18, gap = 4;
            for (int i = 0; i < PRESETS.length; i++) {
                int row = i / 4, col = i % 4;
                int cx = x + 8 + col * (cell + gap);
                int cy = py + 8 + row * (cell + gap);
                if (mx >= cx && mx <= cx + cell && my >= cy && my <= cy + cell) {
                    color = PRESETS[i];
                    if (onChange != null) onChange.run();
                    expanded = false;
                    return true;
                }
            }
            expanded = false;
        }
        return false;
    }

    public int getColor() { return color; }
    public void setColor(int c) { this.color = c; }
    public boolean isExpanded() { return expanded; }
    public void collapse() { this.expanded = false; }
}
