package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class FPS extends Module {
    public FPS() {
        super("FPS", "Displays current game frames per second on HUD", Category.LEGIT);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int fps = mc.getCurrentFps();
        String fpsText = "FPS: " + fps;

        int width = mc.textRenderer.getWidth(fpsText);
        RenderUtils.fill(context, 4, 22, 10 + width, 34, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, 4, 22, 10 + width, 34, 1, ThemeManager.getBorderColor());

        context.drawTextWithShadow(mc.textRenderer, fpsText, 7, 24, 0xFF55FF55);
    }
}
