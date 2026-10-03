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

public class VehicleESP extends Module {
    public final BooleanSetting boats = new BooleanSetting("Boats", "Mark boats", true);
    public final BooleanSetting chestBoats = new BooleanSetting("Chest Boats", "Mark chest boats", true);
    public final BooleanSetting minecarts = new BooleanSetting("Minecarts", "Mark minecarts", true);
    public final BooleanSetting chestMinecarts = new BooleanSetting("Chest Minecarts", "Mark chest minecarts", true);
    public final BooleanSetting other = new BooleanSetting("Other Vehicles", "Mark other vehicles", false);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show vehicle name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", false);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 64.0, 8.0, 160.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 300.0, 50.0, 1000.0, 25.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 170, 0, 255));

    public VehicleESP() {
        super("VehicleESP", "Marks boats and minecarts", Category.RENDER);
        addSetting(boats);
        addSetting(chestBoats);
        addSetting(minecarts);
        addSetting(chestMinecarts);
        addSetting(other);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(box);
        addSetting(tracer);
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
        boolean isBoat = path.contains("boat") && !path.contains("chest");
        boolean isChestBoat = path.contains("chest_boat");
        boolean isCart = path.contains("minecart") && !path.contains("chest") && !path.contains("furnace") && !path.contains("tnt") && !path.contains("hopper");
        boolean isChestCart = path.contains("chest_minecart");
        boolean isOther = (path.contains("minecart") || path.contains("boat")) && !isBoat && !isChestBoat && !isCart && !isChestCart;
        if (isBoat && boats.isEnabled()) return "Boat";
        if (isChestBoat && chestBoats.isEnabled()) return "Chest Boat";
        if (isCart && minecarts.isEnabled()) return "Minecart";
        if (isChestCart && chestMinecarts.isEnabled()) return "Chest Minecart";
        if (isOther && other.isEnabled()) return path;
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
                int[] sc = com.anormal.client.util.ProjectionUtil.project(
                        new Vec3d(e.getX(), e.getY() + 0.8, e.getZ()), tickDelta);
                if (sc == null) continue;
                int col = color.getValue();
                if (box.isEnabled()) {
                    context.fill(sc[0] - 5, sc[1] - 5, sc[0] + 5, sc[1] + 5, (col & 0x00FFFFFF) | 0x55000000);
                    context.fill(sc[0] - 5, sc[1] - 5, sc[0] + 5, sc[1] - 4, col);
                    context.fill(sc[0] - 5, sc[1] + 4, sc[0] + 5, sc[1] + 5, col);
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
                    if (showName.isEnabled()) label.append("§6").append(k);
                    if (showDistance.isEnabled()) {
                        if (label.length() > 0) label.append(" ");
                        label.append("§f").append((int) d).append("m");
                    }
                    if (showCoords.isEnabled()) {
                        if (label.length() > 0) label.append(" ");
                        label.append(String.format("§7%.0f %.0f %.0f", e.getX(), e.getY(), e.getZ()));
                    }
                    if (label.length() > 0) {
                        String text = label.toString();
                        RenderUtils.drawText(context, mc.textRenderer, text,
                                sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 7, 0xFFFFFFFF, true);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
