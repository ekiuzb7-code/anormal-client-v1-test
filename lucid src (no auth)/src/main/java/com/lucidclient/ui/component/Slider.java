package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.util.MathUtils;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A minimalist horizontal slider. Track + circular knob.
 * Value is interpolated between min and max (floats).
 *
 * All drawing goes through {@link RenderUtils} which delegates to the
 * standard 1.21.11 yarn-mapped {@link DrawContext#fill} API.
 */
public class Slider extends Component {
    private final float min;
    private final float max;
    private float value;
    private final int decimals;
    private Runnable onChange;
    private boolean dragging;
    private float dragAnim = 0f;

    public Slider(int x, int y, int w, int h, float min, float max, float initial, int decimals, Runnable onChange) {
        super(x, y, w, h);
        this.min = min;
        this.max = max;
        this.value = initial;
        this.decimals = decimals;
        this.onChange = onChange;
    }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        float target = dragging ? 1f : (hovered ? 0.6f : 0f);
        dragAnim = approach(dragAnim, target, (1f / 140f) * dtMs);
    }

    private float valueToRatio(float v) {
        if (max <= min) return 0f;
        return (v - min) / (max - min);
    }

    private float ratioToValue(float r) {
        return min + r * (max - min);
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        float ratio = valueToRatio(value);
        // Track
        int trackY = y + height / 2 - 2;
        RenderUtils.drawRoundedRect(ctx, x, trackY, width, 4, 2, Colors.BORDER);
        // Filled portion
        int fillW = (int) (width * ratio);
        if (fillW > 0) RenderUtils.drawRoundedRect(ctx, x, trackY, fillW, 4, 2, Colors.ACCENT);

        // Knob
        int knobR = 7 + (int) (dragAnim * 2);
        int knobX = x + (int) (width * ratio);
        int knobY = y + height / 2;
        RenderUtils.drawSoftShadow(ctx, knobX - knobR, knobY - knobR, knobR * 2, knobR * 2, knobR, 3, 30);
        RenderUtils.drawRoundedRect(ctx, knobX - knobR, knobY - knobR, knobR * 2, knobR * 2, knobR, Colors.BACKGROUND);
        RenderUtils.drawRoundedRectBorder(ctx, knobX - knobR, knobY - knobR, knobR * 2, knobR * 2, knobR, Colors.BORDER, 1);

        // Value label
        String valStr = String.format("%." + decimals + "f", value);
        int labelY = RenderUtils.centerTextY(tr, y, height);
        int labelX = x + width + 8;
        RenderUtils.drawText(ctx, tr, valStr, labelX, labelY, Colors.TEXT_SECONDARY, false);
    }

    private void updateFromMouse(double mx) {
        float r = (float) ((mx - x) / width);
        r = MathUtils.clamp(r, 0f, 1f);
        float newVal = ratioToValue(r);
        if (newVal != value) {
            value = newVal;
            if (onChange != null) onChange.run();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !enabled) return false;
        if (button == 0 && contains(mx, my)) {
            dragging = true;
            updateFromMouse(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            updateFromMouse(mx);
            return true;
        }
        return false;
    }

    public float getValue() { return value; }
    public void setValue(float v) {
        this.value = MathUtils.clamp(v, min, max);
    }
}
