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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;

public class ArmorStandESP extends Module {
    public final BooleanSetting outline = new BooleanSetting("Outline", "Glow outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 220, 0, 255));

    public ArmorStandESP() {
        super("ArmorStandESP", "Marks armor stands", Category.RENDER);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ArmorStandEntity)) continue;
            try {
                e.setGlowing(outline.isEnabled() && mc.player.distanceTo(e) <= range.getValue());
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ArmorStandEntity) {
                try {
                    e.setGlowing(false);
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            try {
                if (!(e instanceof ArmorStandEntity) || !e.isAlive()) continue;
                double d = mc.player.distanceTo(e);
                if (d > range.getValue()) continue;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(
                        new Vec3d(e.getX(), e.getY() + 1.0, e.getZ()), tickDelta);
                if (sc == null) continue;
                int col = color.getValue();
                if (box.isEnabled()) {
                    context.fill(sc[0] - 4, sc[1] - 8, sc[0] + 4, sc[1] + 8, (col & 0x00FFFFFF) | 0x44000000);
                    context.fill(sc[0] - 4, sc[1] - 8, sc[0] + 4, sc[1] - 7, col);
                    context.fill(sc[0] - 4, sc[1] + 7, sc[0] + 4, sc[1] + 8, col);
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
                if (showDistance.isEnabled() && mc.textRenderer != null) {
                    String t = (int) d + "m";
                    RenderUtils.drawText(context, mc.textRenderer, t, sc[0] + 6, sc[1] - 4, 0xFFAAAAAA, true);
                }
            } catch (Throwable ignored) {}
        }
    }
}
