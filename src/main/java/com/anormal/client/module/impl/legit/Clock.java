package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Clock extends Module {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public Clock() {
        super("Clock", "Displays real-world local time on HUD", Category.LEGIT);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        String time = LocalTime.now().format(FORMATTER);
        String text = "TIME: " + time;

        int width = mc.textRenderer.getWidth(text);
        RenderUtils.fill(context, 4, 38, 10 + width, 50, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, 4, 38, 10 + width, 50, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, mc.textRenderer, text, 7, 40, 0xFF55FFFF, true);
    }
}
