package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Vec3d;

public class Explosions extends Module {
    public final BooleanSetting blastRing = new BooleanSetting("Blast Ring", "Renders inner block-destruction sphere", true);
    public final ColorSetting damageColor = new ColorSetting("Damage Color", "Outer entity-damage sphere color", ColorUtils.rgba(255, 60, 60, 220));
    public final ColorSetting blastColor = new ColorSetting("Blast Color", "Inner block-damage sphere color", ColorUtils.rgba(255, 180, 40, 220));
    public final BooleanSetting showFuse = new BooleanSetting("Show Fuse", "Shows remaining fuse time on the marker", true);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Only renders TNT within this range", 64.0, 8.0, 128.0, 4.0);

    public Explosions() {
        super("Explosions", "Renders blast damage radius rings around primed TNT", Category.UZNY11);
        addSetting(blastRing);
        addSetting(damageColor);
        addSetting(blastColor);
        addSetting(showFuse);
        addSetting(maxDistance);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        int h = mc.getWindow().getScaledHeight();
        double f = focal(h);
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof TntEntity tnt)) continue;
                double dist = mc.player.distanceTo(e);
                if (dist > maxDistance.getValue() || dist < 0.5) continue;
                int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(e.getX(), e.getY() + 0.5, e.getZ()), tickDelta);
                if (s == null) continue;
                int outer = Math.min(220, (int) (f * 8.0 / dist));
                circle(context, s[0], s[1], outer, damageColor.getValue());
                if (blastRing.isEnabled()) {
                    int inner = Math.min(220, (int) (f * 4.0 / dist));
                    circle(context, s[0], s[1], inner, blastColor.getValue());
                }
                if (showFuse.isEnabled()) {
                    String fuse;
                    try {
                        fuse = String.format("%.1fs", tnt.getFuse() / 20.0);
                    } catch (Throwable t) {
                        fuse = "TNT";
                    }
                    RenderUtils.drawText(context, mc.textRenderer, fuse, s[0] - mc.textRenderer.getWidth(fuse) / 2, s[1] - outer - 10, 0xFFFFFFFF, true);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void circle(DrawContext context, int cx, int cy, int r, int col) {
        if (r < 2) {
            context.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
            return;
        }
        for (int i = 0; i < 28; i++) {
            double a1 = i / 28.0 * 2 * Math.PI, a2 = (i + 1) / 28.0 * 2 * Math.PI;
            line(context, cx + (int) (Math.cos(a1) * r), cy + (int) (Math.sin(a1) * r),
                    cx + (int) (Math.cos(a2) * r), cy + (int) (Math.sin(a2) * r), col);
        }
    }

    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(40, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, col);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, col);
        }
    }

    private double focal(int h) {
        int fov = 70;
        try {
            fov = mc.options.getFov().getValue();
        } catch (Throwable ignored) {}
        return (h / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2.0));
    }
}
