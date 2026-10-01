package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class ReachDisplay extends Module {
    private static double lastReach = 0.0;
    private static long lastHitTime = 0;

    public ReachDisplay() {
        super("ReachDisplay", "Displays the distance of your last landed attack on HUD", Category.LEGIT);
        setEnabled(true);
    }

    public static void updateReach(double reach) {
        lastReach = reach;
        lastHitTime = System.currentTimeMillis();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        if (System.currentTimeMillis() - lastHitTime > 5000) return;

        String reachText = String.format("Reach: %.2f blocks", lastReach);
        int width = mc.textRenderer.getWidth(reachText);
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int x = (screenWidth - width) / 2;
        int y = screenHeight / 2 + 15;

        RenderUtils.fill(context, x - 4, y - 2, x + width + 4, y + 10, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x - 4, y - 2, x + width + 4, y + 10, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, mc.textRenderer, reachText, x, y, 0xFFFFAA00, true);
    }
}
