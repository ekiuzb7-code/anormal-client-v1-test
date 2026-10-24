package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class DirectionHUD extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 960.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 40.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showDegrees = new BooleanSetting("Show Degrees", "Append yaw degrees", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Direction text color", ColorUtils.rgba(255, 220, 0, 255));

    public DirectionHUD() {
        super("DirectionHUD", "Big compass facing letters display", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showDegrees);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            float yaw = (mc.player.getYaw() % 360 + 360) % 360;
            String dir = yaw >= 315 || yaw < 45 ? "S" : (yaw >= 45 && yaw < 135 ? "W" : (yaw >= 135 && yaw < 225 ? "N" : "E"));
            String full = dir.equals("N") ? "North" : dir.equals("S") ? "South" : dir.equals("E") ? "East" : "West";
            String value = full + (showDegrees.isEnabled() ? " (" + (int) yaw + "\u00b0)" : "");
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            com.anormal.client.util.HudStyle.card(context, mc.textRenderer, x, y,
                    "FACING", value, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
