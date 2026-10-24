package com.anormal.client.module.impl.legit;

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
        super("Clock", "Displays real-world local time on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        String time = LocalTime.now().format(FORMATTER);
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        com.anormal.client.util.HudStyle.card(context, mc.textRenderer, x, y,
                "TIME", time, 0xFF55FFFF, false);
    }
}
