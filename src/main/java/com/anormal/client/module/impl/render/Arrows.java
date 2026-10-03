package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Arrows extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Arrow indicator color", ColorUtils.rgba(255, 80, 60, 255));
    public final NumberSetting radiusScale = new NumberSetting("Radius Scale", "Circle size where arrows are displayed", 80.0, 20.0, 220.0, 5.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Shows distance beside indicators under ~170 blocks", true);
    public final BooleanSetting scaleOpacity = new BooleanSetting("Scale Opacity", "Fades indicators with target distance", true);

    public Arrows() {
        super("Arrows", "Displays arrows around screen pointing to players outside FOV", Category.RENDER);
        addSetting(color);
        addSetting(radiusScale);
        addSetting(showDistance);
        addSetting(scaleOpacity);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int cx = w / 2, cy = h / 2;
        int r = radiusScale.getValue().intValue();
        int stacked = 0;
        try {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive()) continue;
                double dist = mc.player.distanceTo(p);
                if (dist > 170 && !showDistance.isEnabled()) continue;
                if (dist < 3) continue;
                int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX(), p.getEyeY(), p.getZ()), tickDelta);
                if (s != null && s[0] > 10 && s[0] < w - 10 && s[1] > 10 && s[1] < h - 10) continue;
                double[] v = cameraSpace(new Vec3d(p.getX(), p.getEyeY(), p.getZ()));
                if (v == null) continue;
                double ang = Math.atan2(-v[1], v[0]);
                int ax = (int) (cx + Math.cos(ang) * r);
                int ay = (int) (cy + Math.sin(ang) * r);
                ax = Math.max(8, Math.min(w - 8, ax));
                ay = Math.max(8, Math.min(h - 8, ay));
                int alpha = scaleOpacity.isEnabled() ? Math.max(70, 255 - (int) (dist * 1.2)) : 255;
                int col = ColorUtils.rgba(color.getRed(), color.getGreen(), color.getBlue(), alpha);
                int tx = (int) (ax + Math.cos(ang) * 7), ty = (int) (ay + Math.sin(ang) * 7);
                int bx = (int) (ax - Math.cos(ang) * 4), by = (int) (ay - Math.sin(ang) * 4);
                int px = (int) (-Math.sin(ang) * 4), py = (int) (Math.cos(ang) * 4);
                line(context, tx, ty, bx + px, by + py, col);
                line(context, tx, ty, bx - px, by - py, col);
                line(context, bx + px, by + py, bx - px, by - py, col);
                if (showDistance.isEnabled() && dist < 170) {
                    String d = (int) dist + "m";
                    RenderUtils.drawText(context, mc.textRenderer, d, ax + 8, ay - 4 + (stacked % 4) * 10, col, true);
                    stacked++;
                }
            }
        } catch (Throwable ignored) {}
    }



    private double[] cameraSpace(Vec3d p) {
        try {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d c = mc.player.getEyePos();
            double dx = p.x - c.x, dy = p.y - c.y, dz = p.z - c.z;
            double yaw = Math.toRadians(cam.getYaw());
            double pitch = Math.toRadians(cam.getPitch());
            double cosP = Math.cos(pitch);
            double fx = -Math.sin(yaw) * cosP, fy = -Math.sin(pitch), fz = Math.cos(yaw) * cosP;
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            return new double[]{dx * rx + dz * rz, dx * ux + dy * uy + dz * uz};
        } catch (Throwable t) {
            return null;
        }
    }

    private double focal(int h) {
        int fov = 70;
        try {
            fov = mc.options.getFov().getValue();
        } catch (Throwable ignored) {}
        return (h / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2.0));
    }

    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(60, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, col);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, col);
        }
    }

    @SuppressWarnings("unused")
    private boolean listed(PlayerEntity p) {
        try {
            if (mc.getNetworkHandler() == null) return true;
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList())
                if (e.getProfile().id().equals(p.getUuid())) return true;
        } catch (Throwable ignored) {}
        return true;
    }
}
