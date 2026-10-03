package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class FPS extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 30.0, 0.0, 1080.0, 1.0);
    public final ColorSetting fpsColor = new ColorSetting("Color", "FPS text color", ColorUtils.rgba(0, 230, 255, 255));

    public FPS() {
        super("FPS", "Displays current client frames per second on HUD", Category.UZNY11);
        addSetting(posX);
        addSetting(posY);
        addSetting(fpsColor);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        String fpsText = "FPS: " + mc.getCurrentFps();
        int textWidth = mc.textRenderer.getWidth(fpsText);

        RenderUtils.fill(context, x - 4, y - 3, x + textWidth + 4, y + 11, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x - 4, y - 3, x + textWidth + 4, y + 11, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, mc.textRenderer, fpsText, x, y, fpsColor.getValue(), true);
    }
}
