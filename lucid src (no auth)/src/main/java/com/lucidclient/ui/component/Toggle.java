package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.MathUtils;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A modern toggle switch — black track when on, gray when off.
 * The knob slides smoothly between positions.
 *
 * Size: 40x22. Drawing goes through {@link RenderUtils} which delegates
 * to the standard 1.21.11 yarn-mapped {@link DrawContext#fill} API.
 */
public class Toggle extends Component {
    private boolean on;
    private float knobPos = 0f; // 0 = off, 1 = on
    private Runnable onChange;

    public Toggle(int x, int y, boolean initial, Runnable onChange) {
        super(x, y, 40, 22);
        this.on = initial;
        this.onChange = onChange;
        this.knobPos = initial ? 1f : 0f;
    }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        float target = on ? 1f : 0f;
        float speed = 1f / 180f;
        knobPos = MathUtils.clamp(approach(knobPos, target, speed * dtMs), 0f, 1f);
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        float eased = knobPos;
        // ease the knob position
        eased = 1f - (1f - eased) * (1f - eased);

        int trackColor = Colors.lerp(Colors.BORDER, Colors.ACCENT, eased);
        RenderUtils.drawRoundedRect(ctx, x, y, width, height, LucidTheme.RADIUS_PILL, trackColor);

        // Knob
        int knobSize = height - 4;
        int knobTravel = width - knobSize - 4;
        int knobX = x + 2 + (int) (knobTravel * eased);
        int knobY = y + 2;
        // Knob shadow
        RenderUtils.drawSoftShadow(ctx, knobX, knobY, knobSize, knobSize, knobSize / 2, 2, 30);
        RenderUtils.drawRoundedRect(ctx, knobX, knobY, knobSize, knobSize, knobSize / 2, Colors.BACKGROUND);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !enabled) return false;
        if (button == 0 && contains(mx, my)) {
            on = !on;
            if (onChange != null) onChange.run();
            return true;
        }
        return false;
    }

    public boolean isOn() { return on; }
    public void setOn(boolean on) {
        if (this.on != on) {
            this.on = on;
            if (onChange != null) onChange.run();
        }
    }
}
