package dev.zaza.client.render;

import dev.zaza.client.render.GlassRenderer.Pane;
import dev.zaza.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2f;

public final class GlassPane {
    private GlassPane() {}

    public static void submit(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                              float radius, int tint) {
        if (width <= 0f || height <= 0f) return;
        Vector2f start = graphics.pose().transformPosition(x, y, new Vector2f());
        Vector2f end = graphics.pose().transformPosition(x + width, y + height, new Vector2f());
        float stretch = Math.abs(end.y - start.y) / Math.max(height, 0.001f);
        float opacity = Colors.alpha(Opacity.apply(0xFFFFFFFF)) / 255f;
        GlassRenderer.submit(new Pane(Math.min(start.x, end.x), Math.min(start.y, end.y),
                Math.abs(end.x - start.x), Math.abs(end.y - start.y), radius * stretch,
                GlassRenderer.REGULAR, tint, -0.33f, -0.94f, 0f,
                opacity, Scissor.current()));
    }
}
