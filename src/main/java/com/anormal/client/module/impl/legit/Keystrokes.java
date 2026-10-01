package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Keystrokes extends Module {
    public final BooleanSetting showSpace = new BooleanSetting("Show Spacebar", "Displays spacebar row", true);
    public final BooleanSetting showLmbRmb = new BooleanSetting("Show Clicks", "Displays LMB and RMB boxes", true);

    public Keystrokes() {
        super("Keystrokes", "Displays keyboard and mouse keystrokes on HUD", Category.LEGIT);
        addSetting(showSpace);
        addSetting(showLmbRmb);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int baseX = 20;
        int baseY = 120;
        int size = 20;
        int gap = 2;

        boolean w = mc.options.forwardKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();

        // W
        drawKey(context, baseX + size + gap, baseY, size, size, "W", w);
        // A, S, D
        drawKey(context, baseX, baseY + size + gap, size, size, "A", a);
        drawKey(context, baseX + size + gap, baseY + size + gap, size, size, "S", s);
        drawKey(context, baseX + (size + gap) * 2, baseY + size + gap, size, size, "D", d);

        int currentY = baseY + (size + gap) * 2;

        if (showLmbRmb.isEnabled()) {
            boolean lmb = mc.options.attackKey.isPressed();
            boolean rmb = mc.options.useKey.isPressed();
            int mouseWidth = (size * 3 + gap * 2 - gap) / 2;
            drawKey(context, baseX, currentY, mouseWidth, size, "LMB", lmb);
            drawKey(context, baseX + mouseWidth + gap, currentY, mouseWidth, size, "RMB", rmb);
            currentY += size + gap;
        }

        if (showSpace.isEnabled()) {
            boolean space = mc.options.jumpKey.isPressed();
            drawKey(context, baseX, currentY, size * 3 + gap * 2, 12, "—", space);
        }
    }

    private void drawKey(DrawContext context, int x, int y, int w, int h, String label, boolean pressed) {
        int bg = pressed ? ThemeManager.getAccentColor() : ThemeManager.getBackgroundColor();
        int textColor = pressed ? 0xFFFFFFFF : ThemeManager.getTextColor(false);

        RenderUtils.fill(context, x, y, x + w, y + h, bg);
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());

        int textW = mc.textRenderer.getWidth(label);
        RenderUtils.drawText(context, mc.textRenderer, label, x + (w - textW) / 2, y + (h - 8) / 2, textColor, true);
    }
}
