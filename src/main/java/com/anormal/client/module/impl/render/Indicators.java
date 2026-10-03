package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;

public class Indicators extends Module {
    public final ModeSetting alertType = new ModeSetting("Alert Type", "Which projectiles get indicators", "Threat", "Always", "Threat", "Hit Only");
    public final ColorSetting color = new ColorSetting("Color", "Common projectile indicator color", ColorUtils.rgba(255, 200, 60, 255));
    public final ColorSetting uncommonColor = new ColorSetting("Uncommon Color", "Color for fireballs and other uncommon projectiles", ColorUtils.rgba(180, 80, 255, 255));
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Indicators for arrows", true);
    public final BooleanSetting pearls = new BooleanSetting("Pearls", "Indicators for pearls", true);
    public final BooleanSetting potions = new BooleanSetting("Potions", "Indicators for potions", true);
    public final BooleanSetting eggs = new BooleanSetting("Eggs", "Indicators for eggs", false);
    public final BooleanSetting snowballs = new BooleanSetting("Snowballs", "Indicators for snowballs", false);
    public final BooleanSetting fireballs = new BooleanSetting("Fireballs", "Indicators for fireballs", true);
    public final NumberSetting radiusScale = new NumberSetting("Radius Scale", "Circle size where indicators are displayed", 70.0, 20.0, 220.0, 5.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Shows distance on the indicator", true);

    public Indicators() {
        super("Indicators", "Displays warning arrows toward incoming projectiles", Category.RENDER);
        addSetting(alertType);
        addSetting(color);
        addSetting(uncommonColor);
        addSetting(arrows);
        addSetting(pearls);
        addSetting(potions);
        addSetting(eggs);
        addSetting(snowballs);
        addSetting(fireballs);
        addSetting(radiusScale);
        addSetting(showDistance);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int cx = w / 2, cy = h / 2;
        int r = radiusScale.getValue().intValue();
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ProjectileEntity)) continue;
                String kind = kind(e);
                if (kind.isEmpty() || !typeEnabled(kind)) continue;
                Vec3d pos = new Vec3d(e.getX(), e.getY(), e.getZ());
                Vec3d vel;
                try {
                    vel = e.getVelocity();
                } catch (Throwable t) {
                    continue;
                }
                if (alertType.is("Threat") && !closing(pos, vel)) continue;
                if (alertType.is("Hit Only") && !willHit(pos, vel)) continue;
                double[] v = cameraSpace(new Vec3d(pos.x, pos.y, pos.z));
                if (v == null) continue;
                double ang = Math.atan2(-v[1], v[0]);
                int ax = Math.max(8, Math.min(w - 8, (int) (cx + Math.cos(ang) * r)));
                int ay = Math.max(8, Math.min(h - 8, (int) (cy + Math.sin(ang) * r)));
                int col = kind.equals("fireball") ? uncommonColor.getValue() : color.getValue();
                int tx = (int) (ax + Math.cos(ang) * 7), ty = (int) (ay + Math.sin(ang) * 7);
                int bx = (int) (ax - Math.cos(ang) * 4), by = (int) (ay - Math.sin(ang) * 4);
                int px = (int) (-Math.sin(ang) * 4), py = (int) (Math.cos(ang) * 4);
                line(context, tx, ty, bx + px, by + py, col);
                line(context, tx, ty, bx - px, by - py, col);
                if (showDistance.isEnabled()) {
                    String d = (int) mc.player.distanceTo(e) + "m";
                    RenderUtils.drawText(context, mc.textRenderer, d, ax + 8, ay - 4, col, true);
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean typeEnabled(String kind) {
        if (kind.equals("arrow")) return arrows.isEnabled();
        if (kind.equals("pearl")) return pearls.isEnabled();
        if (kind.equals("potion")) return potions.isEnabled();
        if (kind.equals("egg")) return eggs.isEnabled();
        if (kind.equals("snowball")) return snowballs.isEnabled();
        return fireballs.isEnabled();
    }

    private String kind(Entity e) {
        String n;
        try {
            n = e.getClass().getSimpleName().toLowerCase();
        } catch (Throwable t) {
            return "";
        }
        if (n.contains("pearl")) return "pearl";
        if (n.contains("potion")) return "potion";
        if (n.contains("egg")) return "egg";
        if (n.contains("snowball")) return "snowball";
        if (n.contains("fireball") || n.contains("skull") || n.contains("dragon")) return "fireball";
        if (n.contains("arrow") || n.contains("trident") || n.contains("spectral")) return "arrow";
        return "";
    }

    private boolean closing(Vec3d pos, Vec3d vel) {
        try {
            Vec3d toMe = mc.player.getEyePos().subtract(pos);
            return vel.dotProduct(toMe) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean willHit(Vec3d pos, Vec3d vel) {
        try {
            Vec3d eye = mc.player.getEyePos();
            Vec3d toMe = eye.subtract(pos);
            double speedSq = vel.lengthSquared();
            if (speedSq < 1e-6) return false;
            double t = Math.max(0, vel.dotProduct(toMe) / speedSq);
            Vec3d closest = pos.add(vel.multiply(t));
            return closest.distanceTo(eye) < 1.4 && vel.multiply(t).length() < 40;
        } catch (Throwable t) {
            return false;
        }
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
}
