package com.anormal.client.module.impl.render;

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
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class DonkeyFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Detection range", 64.0, 16.0, 128.0, 8.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(150, 100, 50, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max entity markers", 32.0, 8.0, 64.0, 1.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);

    private static final class Mark {
        final Vec3d pos;
        final String name;
        final double dist;
        Mark(Vec3d pos, String name, double dist) {
            this.pos = pos;
            this.name = name;
            this.dist = dist;
        }
    }

    private final List<Mark> cache = new ArrayList<>();
    private int ticks = 0;

    public DonkeyFinder() {
        super("DonkeyFinder", "Marks donkeys mules and llamas", Category.RENDER);
        addSetting(range);
        addSetting(color);
        addSetting(maxShown);
        addSetting(showDistance);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 10) return;
        ticks = 0;
        cache.clear();
        try {
            for (Entity e : mc.world.getEntities()) {
                try {
                    if (e == mc.player || !e.isAlive()) continue;
                    String path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
                    if (!path.contains("donkey") && !path.contains("mule") && !path.contains("llama")) continue;
                    double d = mc.player.distanceTo(e);
                    if (d > range.getValue()) continue;
                    cache.add(new Mark(new Vec3d(e.getX(), e.getY() + 1.0, e.getZ()), path, d));
                    if (cache.size() >= maxShown.getValue().intValue()) break;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || cache.isEmpty()) return;
        try {
            int c = color.getValue();
            for (Mark m : cache) {
                int[] sc = com.anormal.client.util.ProjectionUtil.project(m.pos, tickDelta);
                if (sc == null) continue;
                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] + 3, (c & 0x00FFFFFF) | 0x66000000);
                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] - 2, c);
                context.fill(sc[0] - 3, sc[1] + 2, sc[0] + 3, sc[1] + 3, c);
                String text = m.name + (showDistance.isEnabled() ? " " + (int) m.dist + "m" : "");
                RenderUtils.drawText(context, mc.textRenderer, text, sc[0] + 5, sc[1] - 4, c, true);
            }
        } catch (Throwable ignored) {}
    }


}
