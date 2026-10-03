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
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

public class ItemFrameESP extends Module {
    public final BooleanSetting filledOnly = new BooleanSetting("Filled Only", "Only frames holding items", false);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Glow outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 170, 0, 255));

    public ItemFrameESP() {
        super("ItemFrameESP", "Marks item frames, optionally filled only", Category.RENDER);
        addSetting(filledOnly);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(color);
    }

    private boolean filled(ItemFrameEntity frame) {
        try {
            return !frame.getHeldItemStack().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemFrameEntity frame)) continue;
            try {
                boolean show = mc.player.distanceTo(e) <= range.getValue()
                        && (!filledOnly.isEnabled() || filled(frame));
                e.setGlowing(outline.isEnabled() && show);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ItemFrameEntity) {
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
                if (!(e instanceof ItemFrameEntity frame) || !e.isAlive()) continue;
                if (filledOnly.isEnabled() && !filled(frame)) continue;
                double d = mc.player.distanceTo(e);
                if (d > range.getValue()) continue;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(
                        new Vec3d(e.getX(), e.getY() + 0.5, e.getZ()), tickDelta);
                if (sc == null) continue;
                int col = color.getValue();
                if (box.isEnabled()) {
                    context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x55000000);
                    context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] - 3, col);
                    context.fill(sc[0] - 4, sc[1] + 3, sc[0] + 4, sc[1] + 4, col);
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
                    String item = "";
                    try {
                        if (filled(frame)) {
                            item = frame.getHeldItemStack().getName().getString() + " ";
                        }
                    } catch (Throwable ignored) {}
                    String t = item + (int) d + "m";
                    RenderUtils.drawText(context, mc.textRenderer, t, sc[0] + 6, sc[1] - 4, 0xFFAAAAAA, true);
                }
            } catch (Throwable ignored) {}
        }
    }

    private String itemName(Entity e) {
        try {
            return Registries.ENTITY_TYPE.getId(e.getType()).getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
