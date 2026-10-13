package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Off-screen player indicator.
 *
 * <p>Draws a small arrow at the edge of the screen pointing towards every
 * player you cannot currently see, so you always know where people are even
 * when they are behind you or outside your FOV.</p>
 *
 * <p><b>What was wrong before:</b> the direction was computed from a
 * hand-rolled camera basis that used the raw player eye position (tick
 * quantised) instead of the render camera, and the arrow was a 3-pixel
 * scribble that was very hard to see - which is why the module looked like it
 * did nothing. It now uses the shared render camera, draws a real filled
 * triangle with an outline, and shows the player's name and distance.</p>
 */
public class Arrows extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Arrow indicator color", ColorUtils.rgba(255, 80, 60, 255));
    public final NumberSetting radius = new NumberSetting("Radius", "How far from the screen centre the arrows sit", 90.0, 30.0, 260.0, 5.0);
    public final NumberSetting size = new NumberSetting("Size", "Arrow size in pixels", 9.0, 4.0, 22.0, 1.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Ignore players further than this", 170.0, 20.0, 512.0, 10.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show the distance next to each arrow", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show the player's name next to each arrow", false);
    public final BooleanSetting fade = new BooleanSetting("Fade With Distance", "Fade arrows out as players get further away", true);
    public final BooleanSetting showOnScreen = new BooleanSetting("Show When Visible", "Also show arrows for players already on screen", false);

    public Arrows() {
        super("Arrows", "Points towards players that are outside your field of view", Category.RENDER);
        addSetting(color);
        addSetting(radius);
        addSetting(size);
        addSetting(maxDistance);
        addSetting(showDistance);
        addSetting(showName);
        addSetting(fade);
        addSetting(showOnScreen);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int cx = w / 2, cy = h / 2;
        double rad = radius.getValue();
        double sz = size.getValue();
        double maxDist = maxDistance.getValue();

        int baseR = color.getRed(), baseG = color.getGreen(), baseB = color.getBlue();

        try {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive() || p.isInvisible()) continue;

                double dist = mc.player.distanceTo(p);
                if (dist > maxDist) continue;
                if (dist < 2.0) continue;

                Vec3d target = new Vec3d(p.getX(), p.getEyeY(), p.getZ());

                // Already visible on screen? Skip unless the user wants them.
                int[] screen = ProjectionUtil.project(target, tickDelta);
                boolean onScreen = screen != null
                        && screen[0] > 14 && screen[0] < w - 14
                        && screen[1] > 14 && screen[1] < h - 14;
                if (onScreen && !showOnScreen.isEnabled()) continue;

                double[] cs = ProjectionUtil.cameraSpace(target, tickDelta);
                if (cs == null) continue;

                double right = cs[0], up = cs[1], forward = cs[2];

                // Screen-space direction. Screen Y grows downwards, hence -up.
                double dirX = right;
                double dirY = -up;

                double len = Math.hypot(dirX, dirY);
                double angle;
                if (len < 1.0e-3) {
                    // Directly in front or directly behind: no lateral direction.
                    angle = forward >= 0 ? -Math.PI / 2 : Math.PI / 2;
                } else {
                    angle = Math.atan2(dirY, dirX);
                    if (forward < 0) {
                        // Behind the camera: flip so the arrow still points the
                        // right way around the screen.
                        angle = Math.atan2(-dirY, -dirX);
                    }
                }

                int ax = (int) Math.round(cx + Math.cos(angle) * rad);
                int ay = (int) Math.round(cy + Math.sin(angle) * rad);
                ax = Math.max((int) sz + 4, Math.min(w - (int) sz - 4, ax));
                ay = Math.max((int) sz + 4, Math.min(h - (int) sz - 4, ay));

                int alpha = fade.isEnabled()
                        ? Math.max(60, 255 - (int) (dist / maxDist * 195.0))
                        : 255;
                int col = ColorUtils.rgba(baseR, baseG, baseB, alpha);

                drawArrow(context, ax, ay, angle, sz, col, alpha);

                String label = null;
                if (showName.isEnabled()) label = p.getName().getString();
                if (showDistance.isEnabled()) {
                    String d = (int) dist + "m";
                    label = label == null ? d : label + " " + d;
                }
                if (label != null) {
                    int tx = ax + (int) Math.round(Math.cos(angle) * (sz + 4));
                    int ty = ay + (int) Math.round(Math.sin(angle) * (sz + 4));
                    tx -= mc.textRenderer.getWidth(label) / 2;
                    ty -= 4;
                    RenderUtils.drawText(context, mc.textRenderer, label, tx, ty, col, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Filled triangle pointing along {@code angle}, with a dark outline. */
    private void drawArrow(DrawContext context, int x, int y, double angle, double size, int col, int alpha) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double px = -sin, py = cos; // perpendicular

        double tipX = x + cos * size;
        double tipY = y + sin * size;
        double baseX = x - cos * size * 0.7;
        double baseY = y - sin * size * 0.7;
        double half = size * 0.72;

        int lx1 = (int) Math.round(baseX + px * half), ly1 = (int) Math.round(baseY + py * half);
        int lx2 = (int) Math.round(baseX - px * half), ly2 = (int) Math.round(baseY - py * half);
        int tx = (int) Math.round(tipX), ty = (int) Math.round(tipY);

        int outline = ColorUtils.rgba(0, 0, 0, Math.min(255, alpha));
        // Outline: slightly thicker lines under the fill.
        RenderUtils.drawLine(context, tx, ty, lx1, ly1, outline, 3.0f);
        RenderUtils.drawLine(context, tx, ty, lx2, ly2, outline, 3.0f);
        RenderUtils.drawLine(context, lx1, ly1, lx2, ly2, outline, 3.0f);

        fillTriangle(context, tx, ty, lx1, ly1, lx2, ly2, col);
    }

    /** Scanline triangle fill - cheap and good enough for a small marker. */
    private void fillTriangle(DrawContext context, int x1, int y1, int x2, int y2, int x3, int y3, int col) {
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        if (maxY < minY) return;

        for (int y = minY; y <= maxY; y++) {
            int xa = Integer.MAX_VALUE, xb = Integer.MIN_VALUE;
            int[] e1 = edgeX(x1, y1, x2, y2, y);
            int[] e2 = edgeX(x2, y2, x3, y3, y);
            int[] e3 = edgeX(x3, y3, x1, y1, y);
            for (int[] e : new int[][]{e1, e2, e3}) {
                if (e != null) {
                    xa = Math.min(xa, e[0]);
                    xb = Math.max(xb, e[1]);
                }
            }
            if (xa <= xb && xa != Integer.MAX_VALUE) {
                context.fill(xa, y, xb + 1, y + 1, col);
            }
        }
    }

    private int[] edgeX(int x1, int y1, int x2, int y2, int y) {
        int lo = Math.min(y1, y2), hi = Math.max(y1, y2);
        if (y < lo || y > hi) return null;
        if (y1 == y2) return new int[]{Math.min(x1, x2), Math.max(x1, x2)};
        double t = (double) (y - y1) / (double) (y2 - y1);
        int x = (int) Math.round(x1 + (x2 - x1) * t);
        return new int[]{x, x};
    }
}
