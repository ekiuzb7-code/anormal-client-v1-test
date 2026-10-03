package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

public class ProjectileESP extends Module {
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Mark arrows", true);
    public final BooleanSetting spectralArrows = new BooleanSetting("Spectral Arrows", "Mark spectral arrows", true);
    public final BooleanSetting tridents = new BooleanSetting("Tridents", "Mark tridents", true);
    public final BooleanSetting snowballs = new BooleanSetting("Snowballs", "Mark snowballs", false);
    public final BooleanSetting eggs = new BooleanSetting("Eggs", "Mark eggs", false);
    public final BooleanSetting pearls = new BooleanSetting("Ender Pearls", "Mark ender pearls", true);
    public final BooleanSetting fireballs = new BooleanSetting("Fireballs", "Mark fireballs", true);
    public final BooleanSetting skulls = new BooleanSetting("Wither Skulls", "Mark wither skulls", true);
    public final BooleanSetting bobber = new BooleanSetting("Fishing Bobber", "Mark fishing bobbers", false);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show projectile name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", true);
    public final BooleanSetting trajectory = new BooleanSetting("Trajectory", "Draw flight path", true);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 200.0, 50.0, 500.0, 25.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 85, 85, 255));

    public ProjectileESP() {
        super("ProjectileESP", "Marks flying projectiles with paths", Category.RENDER);
        addSetting(arrows);
        addSetting(spectralArrows);
        addSetting(tridents);
        addSetting(snowballs);
        addSetting(eggs);
        addSetting(pearls);
        addSetting(fireballs);
        addSetting(skulls);
        addSetting(bobber);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(box);
        addSetting(tracer);
        addSetting(trajectory);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(color);
    }

    private String kind(Entity e) {
        String path;
        try {
            path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
        } catch (Throwable ignored) {
            return null;
        }
        if (path.equals("arrow") && arrows.isEnabled()) return "Arrow";
        if (path.equals("spectral_arrow") && spectralArrows.isEnabled()) return "Spectral Arrow";
        if (path.contains("trident") && tridents.isEnabled()) return "Trident";
        if (path.equals("snowball") && snowballs.isEnabled()) return "Snowball";
        if (path.equals("egg") && eggs.isEnabled()) return "Egg";
        if (path.equals("ender_pearl") && pearls.isEnabled()) return "Ender Pearl";
        if (path.contains("fireball") && fireballs.isEnabled()) return "Fireball";
        if (path.contains("wither_skull") && skulls.isEnabled()) return "Wither Skull";
        if (path.contains("fishing_bobber") && bobber.isEnabled()) return "Bobber";
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        double maxR = Math.min(range.getValue(), maxDistance.getValue());
        for (Entity e : mc.world.getEntities()) {
            try {
                if (e == mc.player || !e.isAlive()) continue;
                String k = kind(e);
                if (k == null) continue;
                double d = mc.player.distanceTo(e);
                if (d > maxR) continue;
                int col = color.getValue();
                int[] sc = com.anormal.client.util.ProjectionUtil.project(
                        new Vec3d(e.getX(), e.getY() + 0.3, e.getZ()), tickDelta);
                if (trajectory.isEnabled()) {
                    try {
                        Vec3d v = e.getVelocity();
                        Vec3d prev = new Vec3d(e.getX(), e.getY() + 0.3, e.getZ());
                        for (int i = 1; i <= 12; i++) {
                            Vec3d next = new Vec3d(prev.x + v.x * 2, prev.y + v.y * 2 - i * 0.35, prev.z + v.z * 2);
                            int[] a = com.anormal.client.util.ProjectionUtil.project(prev, tickDelta);
                            int[] b = com.anormal.client.util.ProjectionUtil.project(next, tickDelta);
                            if (a != null && b != null) {
                                int steps = Math.min(30, Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1])));
                                for (int s = 0; s <= steps; s++) {
                                    int x = a[0] + (b[0] - a[0]) * s / Math.max(1, steps);
                                    int y = a[1] + (b[1] - a[1]) * s / Math.max(1, steps);
                                    context.fill(x, y, x + 1, y + 1, col);
                                }
                            }
                            prev = next;
                        }
                    } catch (Throwable ignored) {}
                }
                if (sc == null) continue;
                if (box.isEnabled()) {
                    context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] + 3, (col & 0x00FFFFFF) | 0x66000000);
                    context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] - 2, col);
                    context.fill(sc[0] - 3, sc[1] + 2, sc[0] + 3, sc[1] + 3, col);
                }
                if (tracer.isEnabled()) {
                    int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
                    int steps = Math.min(200, Math.max(Math.abs(sc[0] - w / 2), Math.abs(sc[1] - h / 2)));
                    for (int i = 0; i <= steps; i++) {
                        int x = w / 2 + (sc[0] - w / 2) * i / Math.max(1, steps);
                        int y = h / 2 + (sc[1] - h / 2) * i / Math.max(1, steps);
                        context.fill(x, y, x + 1, y + 1, col);
                    }
                }
                if (mc.textRenderer != null) {
                    StringBuilder label = new StringBuilder();
                    if (showName.isEnabled()) label.append("§c").append(k);
                    if (showDistance.isEnabled()) {
                        if (label.length() > 0) label.append(" ");
                        label.append("§f").append((int) d).append("m");
                    }
                    if (label.length() > 0) {
                        String text = label.toString();
                        RenderUtils.drawText(context, mc.textRenderer, text,
                                sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 5, 0xFFFFFFFF, true);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
