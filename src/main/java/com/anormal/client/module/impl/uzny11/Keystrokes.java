package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class Keystrokes extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 20.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 120.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Size scale", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting showCps = new BooleanSetting("Show CPS", "Displays clicks per second on LMB and RMB", true);
    public final BooleanSetting showSpace = new BooleanSetting("Show Spacebar", "Displays spacebar row", true);
    public final BooleanSetting showLmbRmb = new BooleanSetting("Show Clicks", "Displays LMB and RMB boxes", true);
    public final ColorSetting pressedColor = new ColorSetting("Pressed Color", "Active key color", ColorUtils.rgba(255, 120, 0, 255));

    private final List<Long> leftClicks = new ArrayList<>();
    private final List<Long> rightClicks = new ArrayList<>();
    private boolean leftWasPressed = false;
    private boolean rightWasPressed = false;

    public Keystrokes() {
        super("Keystrokes", "Displays customizable keyboard and mouse keystrokes with CPS on HUD", Category.UZNY11);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showCps);
        addSetting(showSpace);
        addSetting(showLmbRmb);
        addSetting(pressedColor);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        long now = System.currentTimeMillis();

        boolean leftDown = mc.options.attackKey.isPressed();
        if (leftDown && !leftWasPressed) {
            leftClicks.add(now);
        }
        leftWasPressed = leftDown;

        boolean rightDown = mc.options.useKey.isPressed();
        if (rightDown && !rightWasPressed) {
            rightClicks.add(now);
        }
        rightWasPressed = rightDown;

        // Clean up clicks older than 1 second (1000ms)
        leftClicks.removeIf(time -> now - time > 1000);
        rightClicks.removeIf(time -> now - time > 1000);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int baseX = posX.getValue().intValue();
        int baseY = posY.getValue().intValue();
        int size = (int) (20 * scale.getValue());
        int gap = (int) (2 * scale.getValue());

        boolean w = mc.options.forwardKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();

        // W
        drawKey(context, baseX + size + gap, baseY, size, size, "W", null, w);
        // A, S, D
        drawKey(context, baseX, baseY + size + gap, size, size, "A", null, a);
        drawKey(context, baseX + size + gap, baseY + size + gap, size, size, "S", null, s);
        drawKey(context, baseX + (size + gap) * 2, baseY + size + gap, size, size, "D", null, d);

        int currentY = baseY + (size + gap) * 2;

        if (showLmbRmb.isEnabled()) {
            boolean lmb = mc.options.attackKey.isPressed();
            boolean rmb = mc.options.useKey.isPressed();
            int mouseWidth = (size * 3 + gap * 2 - gap) / 2;
            int mouseHeight = (int) (22 * scale.getValue());

            String lmbSub = showCps.isEnabled() ? leftClicks.size() + " CPS" : null;
            String rmbSub = showCps.isEnabled() ? rightClicks.size() + " CPS" : null;

            drawKey(context, baseX, currentY, mouseWidth, mouseHeight, "LMB", lmbSub, lmb);
            drawKey(context, baseX + mouseWidth + gap, currentY, mouseWidth, mouseHeight, "RMB", rmbSub, rmb);
            currentY += mouseHeight + gap;
        }

        if (showSpace.isEnabled()) {
            boolean space = mc.options.jumpKey.isPressed();
            int spaceHeight = (int) (12 * scale.getValue());
            drawKey(context, baseX, currentY, size * 3 + gap * 2, spaceHeight, "—", null, space);
        }
    }

    private void drawKey(DrawContext context, int x, int y, int w, int h, String label, String subText, boolean pressed) {
        int bg = pressed ? pressedColor.getValue() : ThemeManager.getBackgroundColor();
        int textColor = pressed ? 0xFFFFFFFF : ThemeManager.getTextColor(false);

        RenderUtils.fill(context, x, y, x + w, y + h, bg);
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());

        if (subText != null) {
            int textW = mc.textRenderer.getWidth(label);
            RenderUtils.drawText(context, mc.textRenderer, label, x + (w - textW) / 2, y + 2, textColor, true);
            int subW = mc.textRenderer.getWidth(subText);
            RenderUtils.drawText(context, mc.textRenderer, subText, x + (w - subW) / 2, y + h - 9, 0xFFAAAAAA, true);
        } else {
            int textW = mc.textRenderer.getWidth(label);
            RenderUtils.drawText(context, mc.textRenderer, label, x + (w - textW) / 2, y + (h - 8) / 2, textColor, true);
        }
    }
}
