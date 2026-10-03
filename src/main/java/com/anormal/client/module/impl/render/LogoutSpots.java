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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public class LogoutSpots extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Logout marker color", ColorUtils.rgba(255, 80, 255, 255));
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max logout markers drawn", 5.0, 1.0, 20.0, 1.0);
    private static final class Spot {
        final String name, dim, time; final double x, y, z;
        Spot(String n, double x, double y, double z, String d, String t) { name = n; this.x = x; this.y = y; this.z = z; dim = d; time = t; }
    }
    private static final class Known {
        String name = "", dim = ""; double x, y, z; long last = 0;
    }
    private final Map<UUID, Known> known = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();
    public LogoutSpots() {
        super("LogoutSpots", "Saves logout positions with markers", Category.RENDER);
        addSetting(color); addSetting(showDistance); addSetting(maxShown);
    }
    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable t) {
            return "";
        }
    }
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            long t = System.currentTimeMillis();
            String dim = curDim();
            Set<UUID> cur = new HashSet<>();
            for (Entity e : mc.world.getEntities()) {
                try {
                    if (!(e instanceof PlayerEntity p) || e == mc.player) continue;
                    UUID id = p.getUuid();
                    cur.add(id);
                    Known k = known.get(id);
                    if (k == null) { k = new Known(); known.put(id, k); }
                    k.name = p.getName().getString();
                    k.x = p.getX(); k.y = p.getY(); k.z = p.getZ(); k.dim = dim; k.last = t;
                } catch (Throwable ignored) {}
            }
            List<UUID> gone = new ArrayList<>();
            for (Map.Entry<UUID, Known> en : known.entrySet()) {
                try {
                    if (!cur.contains(en.getKey()) && t - en.getValue().last > 500) {
                        Known k = en.getValue();
                        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                        spots.add(0, new Spot(k.name, k.x, k.y, k.z, k.dim, time));
                        while (spots.size() > 20) spots.remove(spots.size() - 1);
                        gone.add(en.getKey());
                    }
                } catch (Throwable ignored) {}
            }
            for (UUID id : gone) known.remove(id);
        } catch (Throwable ignored) {}
    }
    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || spots.isEmpty()) return;
        try {
            int col = color.getValue(), drawn = 0, limit = maxShown.getValue().intValue();
            String dim = curDim();
            for (Spot s : spots) {
                if (drawn >= limit) break;
                if (!s.dim.isEmpty() && !s.dim.equals(dim)) continue;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(s.x, s.y + 1.0, s.z), tickDelta);
                if (sc == null) continue;
                drawn++;
                context.fill(sc[0] - 5, sc[1] - 1, sc[0] + 5, sc[1] + 1, col);
                context.fill(sc[0] - 1, sc[1] - 5, sc[0] + 1, sc[1] + 5, col);
                if (mc.textRenderer != null) {
                    String label = "§d" + s.name + " §f" + (int) s.x + "/" + (int) s.y + "/" + (int) s.z;
                    if (showDistance.isEnabled()) {
                        double dx = s.x - mc.player.getX(), dy = s.y - mc.player.getY(), dz = s.z - mc.player.getZ();
                        label += " §7" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                    }
                    RenderUtils.drawText(context, mc.textRenderer, label, sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] + 7, 0xFFFFFFFF, true);
                }
            }
        } catch (Throwable ignored) {}
    }
}
