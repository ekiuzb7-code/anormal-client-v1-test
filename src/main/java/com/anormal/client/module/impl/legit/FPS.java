package com.anormal.client.module.impl.legit;

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
        super("FPS", "Displays current client frames per second on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(fpsColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        com.anormal.client.util.HudStyle.card(context, mc.textRenderer, x, y,
                "FPS", String.valueOf(mc.getCurrentFps()), fpsColor.getValue(), false);
    }
}
