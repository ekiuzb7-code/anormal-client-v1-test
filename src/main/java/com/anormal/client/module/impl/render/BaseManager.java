package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class BaseManager extends Module {
    public final BooleanSetting setA = new BooleanSetting("Set A", "Save current position as A", false);
    public final BooleanSetting setB = new BooleanSetting("Set B", "Save current position as B", false);
    public final BooleanSetting setC = new BooleanSetting("Set C", "Save current position as C", false);
    public final BooleanSetting clearAll = new BooleanSetting("Clear All", "Forget all bases", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final ColorSetting color = new ColorSetting("Color", "Base marker color", ColorUtils.rgba(85, 255, 85, 255));

    private static final class Slot {
        double x, y, z;
        String dim = "";
        boolean has = false;
    }

    private final Slot a = new Slot(), b = new Slot(), c = new Slot();

    public BaseManager() {
        super("BaseManager", "Three base slots with markers", Category.RENDER);
        addSetting(setA);
        addSetting(setB);
        addSetting(setC);
        addSetting(clearAll);
        addSetting(showDistance);
        addSetting(color);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable t) {
            return "";
        }
    }

    private void save(Slot s, String tag) {
        try {
            s.x = mc.player.getX(); s.y = mc.player.getY(); s.z = mc.player.getZ();
            s.dim = curDim(); s.has = true;
            if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(
                    Text.literal("§a[Base " + tag + "] §fsaved " + (int) s.x + "/" + (int) s.y + "/" + (int) s.z));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (clearAll.isEnabled()) {
            try { clearAll.setValue(false); } catch (Throwable ignored) {}
            a.has = false; b.has = false; c.has = false;
            return;
        }
        if (mc.player == null) return;
        if (setA.isEnabled()) { try { setA.setValue(false); } catch (Throwable ignored) {} save(a, "A"); }
        if (setB.isEnabled()) { try { setB.setValue(false); } catch (Throwable ignored) {} save(b, "B"); }
        if (setC.isEnabled()) { try { setC.setValue(false); } catch (Throwable ignored) {} save(c, "C"); }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        draw(context, a, "A");
        draw(context, b, "B");
        draw(context, c, "C");
    }

    private void draw(DrawContext context, Slot s, String tag) {
        if (!s.has || mc.player == null) return;
        try {
            if (!s.dim.isEmpty() && !s.dim.equals(curDim())) return;
            int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(s.x, s.y + 1.0, s.z), 1.0f);
            if (sc == null || mc.textRenderer == null) return;
            int col = color.getValue();
            context.fill(sc[0] - 1, sc[1] - 6, sc[0] + 1, sc[1] + 6, col);
            context.fill(sc[0] - 6, sc[1] - 1, sc[0] + 6, sc[1] + 1, col);
            String label = "§a" + tag + " §f" + (int) s.x + "/" + (int) s.y + "/" + (int) s.z;
            if (showDistance.isEnabled()) {
                double dx = s.x - mc.player.getX(), dy = s.y - mc.player.getY(), dz = s.z - mc.player.getZ();
                label += " §7" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
            }
            RenderUtils.drawText(context, mc.textRenderer, label, sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] + 8, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }

}
