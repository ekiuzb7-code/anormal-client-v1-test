package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.MathUtils;
import com.lucidclient.ui.util.RenderUtils;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A minimalist dropdown selector with an animated expand.
 *
 * Click the closed control to expand the option list; click an option to select it
 * and collapse. Uses {@link Icons#drawChevronDown} as the expand indicator.
 *
 * Scissor clipping uses {@link DrawContext#enableScissor(int, int, int, int)} /
 * {@link DrawContext#disableScissor()} so options don't bleed outside the panel
 * during the height animation.
 */
public class Dropdown extends Component {
    private final List<String> options;
    private int selected;
    private boolean expanded;
    private float expandProgress = 0f;
    private Runnable onChange;
    private final float[] itemHover;

    public Dropdown(int x, int y, int w, int h, List<String> options, int initial, Runnable onChange) {
        super(x, y, w, h);
        this.options = options;
        this.selected = MathUtils.clamp(initial, 0, options.size() - 1);
        this.onChange = onChange;
        this.itemHover = new float[options.size()];
    }

    public Dropdown(int x, int y, int w, int h, String[] opts, int initial, Runnable onChange) {
        this(x, y, w, h, Arrays.asList(opts), initial, onChange);
    }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        float target = expanded ? 1f : 0f;
        expandProgress += (target - expandProgress) * Math.min(1f, (1f / 160f) * dtMs);
        // Fade per-item hover state
        int hovered = computeHoveredItem(lastMouseX, lastMouseY);
        for (int i = 0; i < itemHover.length; i++) {
            float t = (expanded && i == hovered) ? 1f : 0f;
            itemHover[i] = approach(itemHover[i], t, (1f / 120f) * dtMs);
        }
    }

    // Cached mouse position so update() can compute hovered item
    private int lastMouseX = -1, lastMouseY = -1;

    private int computeHoveredItem(int mx, int my) {
        if (!expanded) return -1;
        int itemH = 26;
        int py = y + height + 2;
        for (int i = 0; i < options.size(); i++) {
            int iy = py + i * itemH;
            if (mx >= x && mx <= x + width && my >= iy && my < iy + itemH) return i;
        }
        return -1;
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        float h = easedHover();
        int bg = Colors.lerp(Colors.CARD, Colors.HOVER, h);
        int border = Colors.lerp(Colors.BORDER, Colors.BORDER_STRONG, h);
        RenderUtils.drawRoundedRect(ctx, x, y, width, height, LucidTheme.RADIUS_BUTTON, bg);
        RenderUtils.drawRoundedRectBorder(ctx, x, y, width, height, LucidTheme.RADIUS_BUTTON, border, 1);

        // Selected value
        int textY = RenderUtils.centerTextY(tr, y, height);
        RenderUtils.drawText(ctx, tr, options.get(selected), x + 12, textY, Colors.TEXT_PRIMARY, false);

        // Chevron
        int chevSize = 12;
        Icons.drawChevronDown(ctx, x + width - chevSize - 8, y + (height - chevSize) / 2, chevSize, Colors.TEXT_SECONDARY);

        // Expanded panel
        if (expandProgress > 0.01f) {
            int itemH = 26;
            int panelH = (int) (options.size() * itemH * expandProgress);
            int py = y + height + 2;
            RenderUtils.drawSoftShadow(ctx, x, py, width, panelH, LucidTheme.RADIUS_BUTTON, 5, 40);
            RenderUtils.drawRoundedRect(ctx, x, py, width, panelH, LucidTheme.RADIUS_BUTTON, Colors.CARD);
            RenderUtils.drawRoundedRectBorder(ctx, x, py, width, panelH, LucidTheme.RADIUS_BUTTON, Colors.BORDER, 1);

            // Scissor to clip the option list during the height animation.
            // Use the FULL (un-animated) height so items inside the clipped region
            // stay fully visible — the panel's animated border masks the rest.
            int fullPanelH = options.size() * itemH;
            ctx.enableScissor(x, py, x + width, py + fullPanelH);
            int iy = py;
            for (int i = 0; i < options.size(); i++) {
                float hover = itemHover[i];
                if (hover > 0.01f) {
                    RenderUtils.drawRoundedRect(ctx, x + 4, iy + 2, width - 8, itemH - 4, LucidTheme.RADIUS_SMALL,
                        Colors.withAlpha(Colors.HOVER, (int) (255 * hover)));
                }
                int labelY = iy + (itemH - tr.fontHeight) / 2 + 1;
                int fg = i == selected ? Colors.ACCENT : Colors.TEXT_PRIMARY;
                RenderUtils.drawText(ctx, tr, options.get(i), x + 12, labelY, fg, false);
                iy += itemH;
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
            int itemH = 26;
            int py = y + height + 2;
            for (int i = 0; i < options.size(); i++) {
                int iy = py + i * itemH;
                if (mx >= x && mx <= x + width && my >= iy && my < iy + itemH) {
                    if (selected != i) {
                        selected = i;
                        if (onChange != null) onChange.run();
                    }
                    expanded = false;
                    return true;
                }
            }
            expanded = false; // outside click closes
        }
        return false;
    }

    public int getSelected() { return selected; }
    public String getSelectedValue() { return options.get(selected); }
    public boolean isExpanded() { return expanded; }
    public void collapse() { this.expanded = false; }
}
