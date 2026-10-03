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

public class MemoryHUD extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 270.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showMax = new BooleanSetting("Show Max", "Show max heap line", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Memory text color", ColorUtils.rgba(255, 255, 255, 255));

    public MemoryHUD() {
        super("MemoryHUD", "Shows RAM used total and GC hint", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showMax);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            long total = rt.totalMemory() / 1048576L;
            long max = rt.maxMemory() / 1048576L;
            String line1 = "RAM: " + used + "/" + total + " MB";
            String line2 = showMax.isEnabled() ? "Max: " + max + " MB" : "Free: " + (rt.freeMemory() / 1048576L) + " MB";
            String hint = (max > 0 && (double) used / max > 0.85) ? "GC: restart soon" : "GC: ok";
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = Math.max(mc.textRenderer.getWidth(line1), Math.max(mc.textRenderer.getWidth(line2), mc.textRenderer.getWidth(hint)));
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 37, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 37, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, line1, x, y, textColor.getValue(), true);
            RenderUtils.drawText(context, mc.textRenderer, line2, x, y + 12, textColor.getValue(), true);
            RenderUtils.drawText(context, mc.textRenderer, hint, x, y + 24, 0xFFAAAAAA, true);
        } catch (Throwable ignored) {}
    }
}
