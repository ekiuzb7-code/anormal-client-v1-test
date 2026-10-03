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
public class LogoutESP extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Logout glow color", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show name tag on marker", true);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max logout ghosts drawn", 5.0, 1.0, 20.0, 1.0);
    private static final class Ghost {
        final String name, dim; final double x, y, z;
        Ghost(String n, double x, double y, double z, String d) { name = n; this.x = x; this.y = y; this.z = z; dim = d; }
    }
    private static final class Seen {
        String name = "", dim = ""; double x, y, z; long last = 0;
    }
    private final Map<UUID, Seen> seen = new HashMap<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    public LogoutESP() {
        super("LogoutESP", "Highlights logout spots with glowing markers", Category.RENDER);
        addSetting(color); addSetting(showName); addSetting(maxShown);
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
                    Seen s = seen.get(id);
                    if (s == null) { s = new Seen(); seen.put(id, s); }
                    s.name = p.getName().getString();
                    s.x = p.getX(); s.y = p.getY(); s.z = p.getZ(); s.dim = dim; s.last = t;
                } catch (Throwable ignored) {}
            }
            List<UUID> gone = new ArrayList<>();
            for (Map.Entry<UUID, Seen> en : seen.entrySet()) {
                try {
                    if (!cur.contains(en.getKey()) && t - en.getValue().last > 500) {
                        Seen s = en.getValue();
                        ghosts.add(0, new Ghost(s.name, s.x, s.y, s.z, s.dim));
                        while (ghosts.size() > 20) ghosts.remove(ghosts.size() - 1);
                        gone.add(en.getKey());
                    }
                } catch (Throwable ignored) {}
            }
            for (UUID id : gone) seen.remove(id);
        } catch (Throwable ignored) {}
    }
    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || ghosts.isEmpty()) return;
        try {
            int col = color.getValue(), drawn = 0, limit = maxShown.getValue().intValue();
            String dim = curDim();
            for (Ghost g : ghosts) {
                if (drawn >= limit) break;
                if (!g.dim.isEmpty() && !g.dim.equals(dim)) continue;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(g.x, g.y + 1.0, g.z), tickDelta);
                if (sc == null) continue;
                drawn++;
                context.fill(sc[0] - 7, sc[1] - 7, sc[0] + 7, sc[1] + 7, (col & 0x00FFFFFF) | 0x66000000);
                RenderUtils.drawBorder(context, sc[0] - 7, sc[1] - 7, sc[0] + 7, sc[1] + 7, 1, col);
                if (showName.isEnabled() && mc.textRenderer != null) {
                    String label = "§c" + g.name;
                    RenderUtils.drawText(context, mc.textRenderer, label, sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] - 18, 0xFFFFFFFF, true);
                }
            }
        } catch (Throwable ignored) {}
    }
}
