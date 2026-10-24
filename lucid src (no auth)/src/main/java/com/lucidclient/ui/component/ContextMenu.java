package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.MathUtils;
import com.lucidclient.ui.util.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A compact, modern context menu — rounded, with hover highlight and soft shadow.
 * Appears at a position, dismisses on outside click.
 *
 * NOT a Component — managed centrally by LucidScreen. Has its own appear animation
 * (95%→100% scale-in + fade). The latest rendered width is cached so the
 * (TextRenderer-less) mouseClicked overload can still do accurate hit-testing.
 *
 * All drawing goes through {@link RenderUtils} which delegates to the
 * standard 1.21.11 yarn-mapped {@link DrawContext#fill} /
 * {@link DrawContext#drawText} / {@link TextRenderer#getWidth} /
 * {@link TextRenderer#fontHeight} APIs.
 */
public class ContextMenu {
    public static class Item {
        public final String label;
        public final String iconName; // optional
        public final Runnable action;
        public final boolean destructive;
        public Item(String label, Runnable action) { this(label, null, action, false); }
        public Item(String label, String icon, Runnable action, boolean destructive) {
            this.label = label; this.iconName = icon; this.action = action; this.destructive = destructive;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private int x, y;
    private int renderedWidth = 0;
    private int renderedHeight = 0;
    private boolean open = false;
    private float appearProgress = 0f;
    private int hoveredIndex = -1;
    private float[] hoverProgress;

    private static final int ITEM_H = 30;
    private static final int MIN_W = 150;
    private static final int PAD_X = 14;

    public void open(int x, int y, List<Item> items) {
        this.x = x;
        this.y = y;
        this.items.clear();
        this.items.addAll(items);
        this.hoverProgress = new float[items.size()];
        this.open = true;
        this.appearProgress = 0f;
        this.hoveredIndex = -1;
        this.renderedWidth = MIN_W;
        this.renderedHeight = items.size() * ITEM_H + 8;
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() { return open; }

    /** Compute the rendered width (max of MIN_W and widest label + padding). */
    private int computeWidth(TextRenderer tr) {
        int w = MIN_W;
        for (Item it : items) w = Math.max(w, tr.getWidth(it.label) + PAD_X * 2 + 24);
        return w;
    }

    public void update(long dtMs) {
        if (!open) { appearProgress = approach(appearProgress, 0f, (1f/120f)*dtMs); return; }
        appearProgress = approach(appearProgress, 1f, (1f / 130f) * dtMs);
        if (hoverProgress == null) return;
        for (int i = 0; i < items.size(); i++) {
            float target = (i == hoveredIndex) ? 1f : 0f;
            hoverProgress[i] = approach(hoverProgress[i], target, (1f / 120f) * dtMs);
        }
    }

    private static float approach(float c, float t, float s) {
        if (c < t) return Math.min(t, c + s);
        if (c > t) return Math.max(t, c - s);
        return t;
    }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        if (!open && appearProgress <= 0.01f) return;
        if (items.isEmpty()) return;

        // Compute width based on widest item
        int w = computeWidth(tr);
        this.renderedWidth = w;
        int h = items.size() * ITEM_H + 8;
        this.renderedHeight = h;

        // Update hoveredIndex from cursor — done here so we don't need a separate
        // updateHover call (LucidScreen doesn't have a TextRenderer reference handy
        // for hit-testing during mouseClicked, but it does during render).
        hoveredIndex = -1;
        int hitY = y + 4;
        for (int i = 0; i < items.size(); i++) {
            if (mouseX >= x && mouseX <= x + w && mouseY >= hitY && mouseY < hitY + ITEM_H) {
                hoveredIndex = i;
                break;
            }
            hitY += ITEM_H;
        }

        // Scale-in animation (95% → 100%)
        float scale = MathUtils.lerp(0.95f, 1f, appearProgress);
        int sw = (int) (w * scale);
        int sh = (int) (h * scale);
        int sx = x + (w - sw) / 2;
        int sy = y + (h - sh) / 2;
        int alpha = (int) (255 * appearProgress);

        RenderUtils.drawSoftShadow(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON, 6, 50 * alpha / 255);
        RenderUtils.drawGlow(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON,
            Colors.withAlpha(Colors.AURORA_GLOW_1, alpha),
            Colors.withAlpha(Colors.AURORA_GLOW_2, alpha));
        RenderUtils.drawRoundedRect(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON,
            Colors.withAlpha(Colors.AURORA_GLASS, alpha));
        RenderUtils.drawRoundedRectBorder(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON,
            Colors.withAlpha(Colors.AURORA_BORDER, alpha), 1);
        RenderUtils.drawTopHighlight(ctx, sx, sy, sw, 28 * alpha / 255);

        int itemY = sy + 4;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            float hp = hoverProgress != null && i < hoverProgress.length ? hoverProgress[i] : 0f;
            int hoverAlpha = (int) (hp * alpha);
            // Aurora hover: accent-tinted swipe on the glass surface.
            RenderUtils.drawRoundedRect(ctx, sx + 4, itemY, sw - 8, ITEM_H - 2, LucidTheme.RADIUS_SMALL,
                Colors.lerp(Colors.withAlpha(Colors.AURORA_CARD_HOVER, hoverAlpha),
                            Colors.withAlpha(Colors.AURORA_ACCENT, hoverAlpha), 0.45f));
            // Label
            int textY = itemY + (ITEM_H - tr.fontHeight) / 2 + 1;
            int fg = item.destructive ? Colors.ERROR : Colors.AURORA_TEXT;
            RenderUtils.drawText(ctx, tr, item.label, sx + PAD_X, textY, Colors.withAlpha(fg, alpha), false);
            // Optional icon
            if (item.iconName != null) {
                Icons.drawCategoryIcon(ctx, item.iconName, sx + sw - 24, itemY + 7, 14,
                    Colors.withAlpha(Colors.AURORA_TEXT_DIM, alpha));
            }
            itemY += ITEM_H;
        }
    }

    /**
     * Click handler. Uses the cached {@link #renderedWidth} (set during the last
     * render pass) for hit-testing — this is accurate enough for menus that are
     * already visible (the user can only click a menu that was just rendered).
     */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;
        if (button == 0) {
            int w = renderedWidth > 0 ? renderedWidth : MIN_W;
            int h = renderedHeight > 0 ? renderedHeight : items.size() * ITEM_H + 8;
            // Outside click closes
            if (mx < x || mx > x + w || my < y || my > y + h) {
                close();
                return false;
            }
            // Check item hit
            int itemY = y + 4;
            for (int i = 0; i < items.size(); i++) {
                if (mx >= x && mx <= x + w && my >= itemY && my < itemY + ITEM_H) {
                    if (items.get(i).action != null) items.get(i).action.run();
                    close();
                    return true;
                }
                itemY += ITEM_H;
            }
        }
        return false;
    }
}
