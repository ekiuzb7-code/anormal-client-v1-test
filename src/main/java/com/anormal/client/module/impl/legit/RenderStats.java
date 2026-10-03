package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

public class RenderStats extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Stats text color", ColorUtils.rgba(255, 255, 255, 255));

    public RenderStats() {
        super("RenderStats", "Shows FPS frame time and entities", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            int fps = mc.getCurrentFps();
            double ms = 1000.0 / Math.max(1, fps);
            int ents = 0;
            try {
                if (mc.world != null) {
                    for (Entity e : mc.world.getEntities()) {
                        if (++ents >= 1000) break;
                    }
                }
            } catch (Throwable ignored) {}
            String text = "FPS: " + fps + "  " + String.format("%.1fms", ms) + "  Ents: " + ents;
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
