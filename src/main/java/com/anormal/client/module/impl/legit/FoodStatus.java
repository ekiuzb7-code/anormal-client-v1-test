package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class FoodStatus extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 120.0, 0.0, 1080.0, 1.0);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Hunger text color", ColorUtils.rgba(255, 255, 255, 255));

    public FoodStatus() {
        super("FoodStatus", "Shows hunger and saturation levels", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int food = mc.player.getHungerManager().getFoodLevel();
            float sat = 0.0f;
            try {
                sat = mc.player.getHungerManager().getSaturationLevel();
            } catch (Throwable ignored) {}
            String text = "Hunger: " + food + "/20  Sat: " + String.format("%.1f", sat);
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
