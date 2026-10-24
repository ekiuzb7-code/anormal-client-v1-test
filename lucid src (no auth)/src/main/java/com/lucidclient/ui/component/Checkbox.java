package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.MathUtils;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A modern checkbox — rounded square that fills black when checked,
 * with a checkmark drawn inside.
 *
 * Size: 16x16. Drawing goes through {@link RenderUtils} which delegates
 * to the standard 1.21.11 yarn-mapped {@link DrawContext#fill} API.
 */
public class Checkbox extends Component {
    private boolean checked;
    private String label;
    private Runnable onChange;
    private float checkAnim = 0f;

    public Checkbox(int x, int y, boolean initial, String label, Runnable onChange) {
        super(x, y, 16, 16);
        this.checked = initial;
        this.label = label;
        this.onChange = onChange;
        this.checkAnim = initial ? 1f : 0f;
    }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        float target = checked ? 1f : 0f;
        checkAnim = MathUtils.clamp(approach(checkAnim, target, (1f / 160f) * dtMs), 0f, 1f);
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        float h = easedHover();
        int bg = Colors.lerp(Colors.CARD, Colors.HOVER, h);
        if (checked) bg = Colors.ACCENT;
        int border = checked ? Colors.ACCENT : Colors.lerp(Colors.BORDER, Colors.BORDER_STRONG, h);

        RenderUtils.drawRoundedRect(ctx, x, y, width, height, LucidTheme.RADIUS_SMALL, bg);
        RenderUtils.drawRoundedRectBorder(ctx, x, y, width, height, LucidTheme.RADIUS_SMALL, border, 1);

        if (checkAnim > 0.01f) {
            // Draw check with scale
            int cx = x + width / 2;
            int cy = y + height / 2;
            int s = (int) (12 * checkAnim);
            Icons.drawCheck(ctx, cx - s / 2, cy - s / 2, s, Colors.TEXT_INVERTED);
        }

        if (label != null && !label.isEmpty()) {
            int labelY = RenderUtils.centerTextY(tr, y, height);
            RenderUtils.drawText(ctx, tr, label, x + width + 8, labelY, Colors.TEXT_PRIMARY, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !enabled) return false;
        // Click target includes label
        int hitW = width + (label != null ? 8 + 80 : 0);
        if (button == 0 && mx >= x && mx <= x + hitW && my >= y && my <= y + height) {
            checked = !checked;
            if (onChange != null) onChange.run();
            return true;
        }
        return false;
    }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean c) {
        if (this.checked != c) {
            this.checked = c;
            if (onChange != null) onChange.run();
        }
    }
}
