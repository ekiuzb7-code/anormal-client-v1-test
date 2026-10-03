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
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class DeathPoints extends Module {
    public final NumberSetting maxPoints = new NumberSetting("Max Points", "Death points remembered", 5.0, 1.0, 20.0, 1.0);
    public final BooleanSetting logInChat = new BooleanSetting("Log In Chat", "Log death coords in chat", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final ColorSetting color = new ColorSetting("Color", "Death marker color", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting clearPoints = new BooleanSetting("Clear", "Forget all death points", false);

    private static final class DeathPoint {
        final double x, y, z;
        final String dim;
        final String time;
        DeathPoint(double x, double y, double z, String dim, String time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.time = time;
        }
    }

    private final List<DeathPoint> points = new ArrayList<>();
    private boolean wasAlive = true;

    public DeathPoints() {
        super("DeathPoints", "Remembers where you died with markers", Category.UZNY11);
        addSetting(maxPoints);
        addSetting(logInChat);
        addSetting(showDistance);
        addSetting(color);
        addSetting(clearPoints);
    }

    @Override
    public void onTick() {
        if (clearPoints.isEnabled()) {
            clearPoints.setValue(false);
            points.clear();
            return;
        }
        if (mc.player == null || mc.world == null) return;
        boolean alive = mc.player.isAlive();
        if (wasAlive && !alive) {
            onDeath();
        }
        wasAlive = alive;
    }

    private void onDeath() {
        try {
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            String dim = "overworld";
            try {
                dim = mc.world.getRegistryKey().getValue().getPath();
            } catch (Throwable ignored) {}
            String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            points.add(0, new DeathPoint(x, y, z, dim, time));
            while (points.size() > maxPoints.getValue().intValue()) {
                points.remove(points.size() - 1);
            }
            if (logInChat.isEnabled() && mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        String.format("§c[Death] §f%.0f / %.0f / %.0f (%s) %s", x, y, z, dim, time)));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || points.isEmpty()) return;
        int col = color.getValue();
        String curDim = "";
        try {
            curDim = mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {}
        for (DeathPoint p : points) {
            if (!p.dim.equals(curDim)) continue;
            int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.x, p.y + 1.0, p.z), tickDelta);
            if (sc == null) continue;
            // Skull-ish marker: cross + coords
            context.fill(sc[0] - 5, sc[1] - 1, sc[0] + 5, sc[1] + 1, col);
            context.fill(sc[0] - 1, sc[1] - 5, sc[0] + 1, sc[1] + 5, col);
            if (mc.textRenderer != null) {
                String label = String.format("§c%.0f/%.0f/%.0f", p.x, p.y, p.z);
                if (showDistance.isEnabled()) {
                    double dx = p.x - mc.player.getX(), dy = p.y - mc.player.getY(), dz = p.z - mc.player.getZ();
                    label += " §7" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                }
                RenderUtils.drawText(context, mc.textRenderer, label,
                        sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] + 7, 0xFFFFFFFF, true);
            }
        }
    }
}
