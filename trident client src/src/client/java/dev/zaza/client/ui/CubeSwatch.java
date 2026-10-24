package dev.zaza.client.ui;

import dev.zaza.client.render.Draw;
import dev.zaza.client.render.Opacity;
import dev.zaza.client.render.Scissor;
import dev.zaza.client.render.Text;
import dev.zaza.client.render.Transform;
import dev.zaza.client.render.state.QuadState;
import dev.zaza.client.render.state.Submit;
import dev.zaza.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2f;

/**
 * Draws the isometric 3D cube swatches seen on the theme cards in the
 * reference screenshots: a bright top face, a mid left face and a dark
 * right face, rendered as three shaded quads.
 */
public final class CubeSwatch {
    private CubeSwatch() {}

    public static void draw(GuiGraphicsExtractor graphics, float x, float y, float size, int accent) {
        if (size <= 0.5f) return;
        int top = Colors.lighten(accent, 0.25f);
        int left = accent;
        int right = Colors.darken(accent, 0.42f);
        float cx = x + size / 2f;
        float half = size * 0.42f;
        float quarter = half * 0.5f;
        // Top face (rhombus).
        quad(graphics, cx, y, cx + half, y + quarter, cx, y + 2 * quarter, cx - half, y + quarter, top);
        // Left face.
        quad(graphics, cx - half, y + quarter, cx, y + 2 * quarter, cx, y + size, cx - half, y + size - quarter, left);
        // Right face.
        quad(graphics, cx + half, y + quarter, cx, y + 2 * quarter, cx, y + size, cx + half, y + size - quarter, right);
    }

    private static void quad(GuiGraphicsExtractor graphics, float ax, float ay, float bx, float by,
                             float cx, float cy, float dx, float dy, int color) {
        color = Opacity.apply(color);
        if (Colors.transparent(color)) return;
        Submit.submit(graphics, new QuadState(new org.joml.Matrix3x2f(graphics.pose()),
                ax, ay, bx, by, cx, cy, dx, dy, color));
    }
}
