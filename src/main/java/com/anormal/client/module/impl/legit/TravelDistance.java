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

public class TravelDistance extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 330.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting reset = new BooleanSetting("Reset", "Reset session distance", false);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Distance text color", ColorUtils.rgba(255, 255, 255, 255));

    private double total = 0.0;
    private double session = 0.0;
    private double lastX = 0.0;
    private double lastY = 0.0;
    private double lastZ = 0.0;
    private boolean init = false;

    public TravelDistance() {
        super("TravelDistance", "Tracks distance walked total and session", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(reset);
        addSetting(textColor);
    }

    @Override
    public void onEnable() {
        init = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (reset.isEnabled()) {
                reset.setValue(false);
                session = 0.0;
            }
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            if (!init) {
                lastX = x;
                lastY = y;
                lastZ = z;
                init = true;
                return;
            }
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 50.0) {
                total += d;
                session += d;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            String text = "Walked: " + (int) session + "m (total " + (int) total + "m)";
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
