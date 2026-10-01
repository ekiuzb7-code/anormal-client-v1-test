package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class Radar extends Module {
    public Radar() {
        super("Radar", "Displays a 2D minimap radar showing nearby players", Category.LEGIT);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        int size = 70;
        int x = 10;
        int y = 50;

        RenderUtils.fill(context, x, y, x + size, y + size, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + size, y + size, 1, ThemeManager.getBorderColor());

        // Center cross
        context.fill(x + size / 2, y, x + size / 2 + 1, y + size, ThemeManager.getBorderColor());
        context.fill(x, y + size / 2, x + size, y + size / 2 + 1, ThemeManager.getBorderColor());

        // Player dot
        context.fill(x + size / 2 - 1, y + size / 2 - 1, x + size / 2 + 2, y + size / 2 + 2, 0xFFFFFFFF);

        // Nearby entity dots
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity && e != mc.player) {
                double diffX = (e.getX() - mc.player.getX()) * 0.8;
                double diffZ = (e.getZ() - mc.player.getZ()) * 0.8;

                int dotX = (int) (x + size / 2 + diffX);
                int dotY = (int) (y + size / 2 + diffZ);

                if (dotX >= x + 2 && dotX <= x + size - 2 && dotY >= y + 2 && dotY <= y + size - 2) {
                    context.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, 0xFFFF4444);
                }
            }
        }
    }
}
