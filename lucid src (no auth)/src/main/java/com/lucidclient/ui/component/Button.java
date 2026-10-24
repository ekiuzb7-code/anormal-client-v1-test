package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.MathUtils;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A premium white button with rounded corners, hover lift, soft shadow,
 * and a 98% press scale. Supports an optional icon.
 *
 * All drawing goes through {@link RenderUtils} which delegates to the
 * standard 1.21.11 yarn-mapped {@link DrawContext#fill} / {@link DrawContext#drawText}.
 */
public class Button extends Component {
    public enum Style { PRIMARY, SECONDARY, GHOST }

    private String label;
    private Style style = Style.SECONDARY;
    private Runnable onClick;
    private String iconName; // optional

    public Button(int x, int y, int w, int h, String label, Runnable onClick) {
        super(x, y, w, h);
        this.label = label;
        this.onClick = onClick;
    }

    public Button withStyle(Style s) { this.style = s; return this; }
    public Button withIcon(String name) { this.iconName = name; return this; }

    public void setLabel(String label) { this.label = label; }
    public void setOnClick(Runnable r) { this.onClick = r; }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        float h = easedHover();
        // Press scale (98% when pressed) — classic look
        float scale = MathUtils.lerp(1f, 0.98f, pressProgress);
        int sw = (int) (width * scale);
        int sh = (int) (height * scale);
        int sx = x + (width - sw) / 2;
        int sy = y + (height - sh) / 2;

        int bg, fg, border;
        switch (style) {
            case PRIMARY:
                // Aurora accent gradient surface; brightens on hover.
                bg = Colors.lerp(Colors.AURORA_ACCENT, Colors.AURORA_ACCENT_2, 0.35f + 0.25f * h);
                fg = Colors.AURORA_TEXT;
                border = Colors.AURORA_ACCENT_HOVER;
                break;
            case GHOST:
                // Translucent glass surface; accent border on hover.
                bg = Colors.withAlpha(Colors.AURORA_GLASS, (int) (110 + 40 * h));
                fg = Colors.AURORA_TEXT;
                border = Colors.withAlpha(h > 0.3f ? Colors.AURORA_ACCENT : Colors.AURORA_BORDER_STRONG, (int) (120 + 80 * h));
                break;
            default: // SECONDARY — glass card surface with accent border on hover
                bg = Colors.withAlpha(Colors.AURORA_GLASS_LIGHT, (int) (220 + 30 * h));
                fg = Colors.AURORA_TEXT;
                border = Colors.lerp(Colors.AURORA_BORDER, Colors.AURORA_ACCENT, h);
        }

        // Aurora: soft glow under primary buttons, rounded glass body.
        if (style == Style.PRIMARY) {
            RenderUtils.drawGlow(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON,
                Colors.withAlpha(Colors.AURORA_GLOW_1, (int) (255 * (0.5f + 0.5f * h))),
                Colors.withAlpha(Colors.AURORA_GLOW_2, (int) (255 * (0.35f + 0.35f * h))));
        }
        RenderUtils.drawRoundedRect(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON, bg);
        RenderUtils.drawRoundedRectBorder(ctx, sx, sy, sw, sh, LucidTheme.RADIUS_BUTTON, border, 1);
        RenderUtils.drawTopHighlight(ctx, sx, sy, sw, 24);

        // Label (centered) — sharp non-shadow text for the classic look.
        int labelY = RenderUtils.centerTextY(tr, sy, sh);
        if (iconName != null) {
            int iconSize = 14;
            int iconX = sx + 12;
            int iconY = sy + (sh - iconSize) / 2;
            Icons.drawCategoryIcon(ctx, iconName, iconX, iconY, iconSize, fg);
            int textX = iconX + iconSize + 8;
            RenderUtils.drawText(ctx, tr, label, textX, labelY, fg, false);
        } else {
            int textX = RenderUtils.centerTextX(tr, label, sx, sw);
            RenderUtils.drawText(ctx, tr, label, textX, labelY, fg, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !enabled) return false;
        if (button == 0 && contains(mx, my)) {
            pressed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pressed) {
            pressed = false;
            if (contains(mx, my) && onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }
}
