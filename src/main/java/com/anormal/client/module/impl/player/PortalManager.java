package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class PortalManager extends Module {
    public final BooleanSetting addHere = new BooleanSetting("Add Portal Here", "Save current position as portal", false);
    public final NumberSetting maxPortals = new NumberSetting("Max Portals", "Max stored portals", 10.0, 1.0, 30.0, 1.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final BooleanSetting clear = new BooleanSetting("Clear All", "Forget all portals", false);

    private static final class Portal {
        final double x, y, z; final String dim;
        Portal(double x, double y, double z, String dim) { this.x = x; this.y = y; this.z = z; this.dim = dim; }
    }

    private final List<Portal> portals = new ArrayList<>();

    public PortalManager() {
        super("PortalManager", "Manual nether portal waypoint list", Category.PLAYER);
        addSetting(addHere);
        addSetting(maxPortals);
        addSetting(showDistance);
        addSetting(clear);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (clear.isEnabled()) {
                clear.setValue(false);
                portals.clear();
                return;
            }
            if (!addHere.isEnabled()) return;
            addHere.setValue(false);
            if (portals.size() >= maxPortals.getValue().intValue()) portals.remove(0);
            portals.add(new Portal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), curDim()));
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("Portal saved (" + portals.size() + ")"));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || portals.isEmpty()) return;
        try {
            String dim = curDim();
            for (int i = 0; i < portals.size(); i++) {
                Portal p = portals.get(i);
                if (!p.dim.isEmpty() && !p.dim.equals(dim)) continue;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.x, p.y + 1.0, p.z), tickDelta);
                if (sc == null) continue;
                int col = 0xFFAA55FF;
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] - 3, col);
                context.fill(sc[0] - 4, sc[1] + 3, sc[0] + 4, sc[1] + 4, col);
                String text = "Portal " + (i + 1);
                if (showDistance.isEnabled()) {
                    double dx = p.x - mc.player.getX();
                    double dy = p.y - mc.player.getY();
                    double dz = p.z - mc.player.getZ();
                    text += " " + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                }
                RenderUtils.drawText(context, mc.textRenderer, text, sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 6, 0xFFFFFFFF, true);
            }
        } catch (Throwable ignored) {}
    }

}
