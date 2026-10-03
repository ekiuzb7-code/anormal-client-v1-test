package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Compass extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen (center-based)", 960.0, -960.0, 2880.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 4.0, 0.0, 1080.0, 1.0);

    public Compass() {
        super("Compass", "Displays a directional compass ribbon on the top HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        int width = 100;
        int x = posX.getValue().intValue() - width / 2;
        int y = posY.getValue().intValue();

        RenderUtils.fill(context, x, y, x + width, y + 14, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + width, y + 14, 1, ThemeManager.getBorderColor());

        float yaw = (mc.player.getYaw() % 360 + 360) % 360;
        String dir = yaw >= 315 || yaw < 45 ? "S" : (yaw >= 45 && yaw < 135 ? "W" : (yaw >= 135 && yaw < 225 ? "N" : "E"));
        String text = "🧭 " + dir + " [" + (int) yaw + "°]";
        int tw = mc.textRenderer.getWidth(text);

        RenderUtils.drawText(context, mc.textRenderer, text, x + (width - tw) / 2, y + 3, ThemeManager.getAccentColor(), true);
    }
}
