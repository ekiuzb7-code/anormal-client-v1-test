package dev.zaza.client.ui;

import dev.zaza.client.render.Draw;
import dev.zaza.client.render.Text;
import dev.zaza.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class SliderBar {
    private final double min;
    private final double max;
    private final IntSupplier get;
    private final IntConsumer set;
    private float x;
    private float y;
    private float height;
    private float scale;
    private float trackWidth;

    public SliderBar(double min, double max, IntSupplier get, IntConsumer set) {
        this.min = min;
        this.max = max;
        this.get = get;
        this.set = set;
        this.trackWidth = 40f;
    }

    public void place(float x, float y, float height, float scale) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.scale = scale;
    }

    public void trackWidth(float width) {
        this.trackWidth = Math.max(4f, width);
    }

    public boolean contains(double px, double py) {
        return px >= x - 4 && px <= x + trackWidth + 4 && py >= y - 4 && py <= y + height + 4;
    }

    public boolean click(double px, double py) {
        if (!contains(px, py)) return false;
        move(px);
        return true;
    }

    public void drag(double px) {
        move(px);
    }

    public void release() {
    }

    private void move(double px) {
        float fraction = Mth.clamp((float) ((px - x) / trackWidth), 0f, 1f);
        set.accept((int) Math.round(min + fraction * (max - min)));
    }

    public void draw(GuiGraphicsExtractor graphics, String valueText, boolean rightLabel) {
        float percent = (float) ((get.getAsInt() - min) / (max - min));
        float trackY = y + height / 2f - 1f * scale;
        float trackH = 2f * scale;
        float knob = 4.5f * scale;
        float fill = trackWidth * percent;
        Draw.rect(graphics, x, trackY, trackWidth, trackH, trackH / 2f, Theme.TRACK);
        Draw.rect(graphics, x, trackY, Math.max(trackH, fill), trackH, trackH / 2f, Theme.ACCENT);
        float knobX = x + fill - knob / 2f;
        float knobY = trackY + trackH / 2f - knob / 2f;
        Draw.rect(graphics, knobX, knobY, knob, knob, knob / 2f,
                Colors.lighten(Theme.ACCENT, 0.25f));
        if (valueText != null) {
            float size = 5.6f * scale;
            if (rightLabel) {
                Text.drawCentered(graphics, valueText, x + trackWidth + 6 * scale, y + height / 2f, size, Theme.MUTED);
            } else {
                Text.drawCentered(graphics, valueText, x - Text.width(valueText, size) - 6 * scale,
                        y + height / 2f, size, Theme.MUTED);
            }
        }
    }
}
