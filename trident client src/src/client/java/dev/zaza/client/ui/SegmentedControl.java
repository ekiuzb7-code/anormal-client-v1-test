package dev.zaza.client.ui;

import dev.zaza.client.render.Draw;
import dev.zaza.client.render.Text;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class SegmentedControl {
    private final String[] labels;
    private final IntSupplier get;
    private final IntConsumer set;
    private final Transition highlight;
    private final float textSize;
    private float x;
    private float y;
    private float height;
    private float scale;

    public SegmentedControl(String[] labels, IntSupplier get, IntConsumer set) {
        this.labels = labels;
        this.get = get;
        this.set = set;
        this.highlight = new Transition(get.getAsInt(), 190, Transition.Easing.EASE_OUT_EXPO);
        this.textSize = 6.5f;
    }

    public float width(float scale) {
        float widest = 0;
        for (String label : labels) widest = Math.max(widest, Text.width(label, textSize * scale));
        return labels.length * (widest + 12 * scale);
    }

    public void place(float x, float y, float height, float scale) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.scale = scale;
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= x + width(scale) && py >= y - 2 && py <= y + height + 2;
    }

    public boolean click(double px, double py) {
        if (!contains(px, py)) return false;
        int index = Math.clamp((int) ((px - x) / (width(scale) / labels.length)), 0, labels.length - 1);
        set.accept(index);
        return true;
    }

    public void draw(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        float width = width(scale);
        float segment = width / labels.length;
        Draw.rect(graphics, x, y, width, height, height * 0.5f, Theme.CONTROL);
        highlight.set(get.getAsInt());
        float index = Math.clamp(highlight.value(), 0f, labels.length - 1f);
        boolean hovered = contains(mouseX, mouseY);
        float wide = segment * (1f + 0.16f * highlight.flight());
        float slot = Math.clamp(x + segment * index - (wide - segment) * 0.5f, x, x + width - wide);
        Draw.rect(graphics, slot, y, wide, height, height * 0.5f,
                hovered ? ThemeAdjust.controlHover : Theme.CONTROL_ACTIVE);
        for (int i = 0; i < labels.length; i++) {
            int color = get.getAsInt() == i ? Theme.TEXT : Theme.DIM;
            Text.drawCenteredX(graphics, labels[i], x + segment * (i + 0.5f), y + height / 2f,
                    textSize * scale, color);
        }
    }
}
