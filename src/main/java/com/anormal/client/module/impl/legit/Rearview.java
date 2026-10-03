package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Rearview extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 1750.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting range = new NumberSetting("Range", "Detection range", 40.0, 10.0, 150.0, 5.0);

    public Rearview() {
        super("Rearview", "Warns about players approaching from behind you", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(range);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;

        Vec3d look = mc.player.getRotationVec(1.0f);
        List<String> behind = new ArrayList<>();
        int maxW = 0;

        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity p && p != mc.player && p.isAlive()) {
                double dist = mc.player.distanceTo(p);
                if (dist > range.getValue()) continue;
                Vec3d to = new Vec3d(p.getX() - mc.player.getX(), 0, p.getZ() - mc.player.getZ()).normalize();
                double dot = look.x * to.x + look.z * to.z;
                if (dot < -0.3) {
                    String line = "⚠ " + p.getName().getString() + " " + (int) dist + "m";
                    behind.add(line);
                    maxW = Math.max(maxW, mc.textRenderer.getWidth(line));
                }
            }
        }

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        String title = "👁 BEHIND (" + behind.size() + ")";

        maxW = Math.max(maxW, mc.textRenderer.getWidth(title));
        if (behind.isEmpty()) {
            maxW = Math.max(maxW, mc.textRenderer.getWidth("§7clear"));
        }

        int w = maxW + 12;
        int h = 14 + Math.max(1, behind.size()) * 11;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        int border = behind.isEmpty() ? ThemeManager.getBorderColor() : 0xFFFF5555;
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, border);
        RenderUtils.drawText(context, mc.textRenderer, title, x + 6, y + 3, 0xFFFFAA00, true);

        int ly = y + 14;
        if (behind.isEmpty()) {
            RenderUtils.drawText(context, mc.textRenderer, "§7clear", x + 6, ly, 0xFFAAAAAA, true);
        } else {
            for (String line : behind) {
                RenderUtils.drawText(context, mc.textRenderer, line, x + 6, ly, 0xFFFF6666, true);
                ly += 11;
            }
        }
    }
}
