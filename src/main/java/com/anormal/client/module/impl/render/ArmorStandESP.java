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
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Marks armor stands.
 *
 * <p><b>Jitter fix:</b> the marker used to be projected from the raw,
 * tick-quantised entity position ({@code getX()/getY()/getZ()}), and the box
 * was a fixed 8x16 pixel square. Because the world is rendered with
 * interpolated positions, the marker visibly flickered and bounced while you
 * moved. It now uses {@link Entity#getLerpedPos(float)} and the entity's real
 * AABB, so it is perfectly smooth.</p>
 */
public class ArmorStandESP extends Module {
    public final BooleanSetting glow = new BooleanSetting("Glow", "Give armor stands a glowing outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw a box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from the crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show the distance", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show the armor stand's custom name", false);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", "Tracer/box line width", 1.0, 0.5, 3.0, 0.5);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 220, 0, 255));

    public ArmorStandESP() {
        super("ArmorStandESP", "Marks armor stands", Category.RENDER);
        addSetting(glow);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(showName);
        addSetting(range);
        addSetting(lineWidth);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        double max = range.getValue();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ArmorStandEntity)) continue;
            try {
                e.setGlowing(glow.isEnabled() && mc.player.distanceTo(e) <= max);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ArmorStandEntity) {
                try {
                    e.setGlowing(false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        float lw = lineWidth.getValue().floatValue();
        int col = color.getValue();

        for (Entity e : mc.world.getEntities()) {
            try {
                if (!(e instanceof ArmorStandEntity) || !e.isAlive()) continue;
                double d = mc.player.distanceTo(e);
                if (d > range.getValue()) continue;

                // Interpolated position + real AABB: no jitter.
                Vec3d lerped = ProjectionUtil.lerpPos(e, tickDelta);
                Box bb = e.getBoundingBox();
                double hgt = Math.max(0.2, bb.maxY - bb.minY);
                double wdt = Math.max(0.2, Math.max(bb.maxX - bb.minX, bb.maxZ - bb.minZ));

                double cyMid = lerped.y + hgt / 2.0;

                int[] sTop = ProjectionUtil.project(new Vec3d(lerped.x, cyMid + hgt / 2.0, lerped.z), tickDelta);
                int[] sBot = ProjectionUtil.project(new Vec3d(lerped.x, cyMid - hgt / 2.0, lerped.z), tickDelta);
                int[] sMid = ProjectionUtil.project(new Vec3d(lerped.x, cyMid, lerped.z), tickDelta);
                if (sTop == null || sBot == null || sMid == null) continue;

                int bh = Math.max(4, Math.abs(sBot[1] - sTop[1]));
                int bw = Math.max(4, (int) (bh * (wdt / hgt)));
                int x = sMid[0];
                int yBot = Math.max(sTop[1], sBot[1]);
                int yTop = yBot - bh;

                if (box.isEnabled()) {
                    RenderUtils.drawLine(context, x - bw / 2, yTop, x + bw / 2, yTop, col, lw);
                    RenderUtils.drawLine(context, x - bw / 2, yBot, x + bw / 2, yBot, col, lw);
                    RenderUtils.drawLine(context, x - bw / 2, yTop, x - bw / 2, yBot, col, lw);
                    RenderUtils.drawLine(context, x + bw / 2, yTop, x + bw / 2, yBot, col, lw);
                }
                if (tracer.isEnabled()) {
                    RenderUtils.drawLine(context, w / 2, h / 2, x, yTop, col, lw);
                }
                if (mc.textRenderer != null) {
                    String label = null;
                    if (showName.isEnabled() && e.hasCustomName()) {
                        label = e.getCustomName() != null ? e.getCustomName().getString() : null;
                    }
                    if (showDistance.isEnabled()) {
                        String dd = (int) d + "m";
                        label = label == null ? dd : label + " " + dd;
                    }
                    if (label != null) {
                        RenderUtils.drawText(context, mc.textRenderer, label,
                                x - mc.textRenderer.getWidth(label) / 2, yTop - 11, col, true);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
