package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Clock extends Module {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 4.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 38.0, 0.0, 1080.0, 1.0);

    public Clock() {
        super("Clock", "Displays real-world local time on HUD", Category.UZNY11);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        String time = LocalTime.now().format(FORMATTER);
        String text = "TIME: " + time;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        int width = mc.textRenderer.getWidth(text);
        RenderUtils.fill(context, x, y, x + width + 6, y + 12, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + width + 6, y + 12, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, mc.textRenderer, text, x + 3, y + 2, 0xFF55FFFF, true);
    }
}
