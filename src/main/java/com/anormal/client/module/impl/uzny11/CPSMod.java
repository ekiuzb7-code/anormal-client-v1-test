package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import java.util.ArrayDeque;

public class CPSMod extends Module {
    public final BooleanSetting showLeft = new BooleanSetting("Show Left", "Show left CPS", true);
    public final BooleanSetting showRight = new BooleanSetting("Show Right", "Show right CPS", true);
    public final BooleanSetting renderBackground = new BooleanSetting("Render Background", "Draw background box", true);
    private final ArrayDeque<Long> left = new ArrayDeque<>();
    private final ArrayDeque<Long> right = new ArrayDeque<>();
    private boolean wasLeft = false;
    private boolean wasRight = false;

    public CPSMod() {
        super("CPSMod", "Displays clicks per second", Category.UZNY11);
        addSetting(showLeft); addSetting(showRight); addSetting(renderBackground);
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        try {
            long now = System.currentTimeMillis();
            boolean l = mc.options.attackKey.isPressed();
            boolean r = mc.options.useKey.isPressed();
            if (l && !wasLeft) left.addLast(now);
            if (r && !wasRight) right.addLast(now);
            wasLeft = l; wasRight = r;
            while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
            while (!right.isEmpty() && now - right.peekFirst() > 1000) right.pollFirst();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null || mc.getWindow() == null) return;
        try {
            String t = "";
            if (showLeft.getValue()) t += left.size() + " ";
            if (showLeft.getValue() && showRight.getValue()) t += "| ";
            if (showRight.getValue()) t += right.size();
            t += " CPS";
            int x = 10, y = 50;
            if (renderBackground.getValue()) context.fill(x - 2, y - 2, x + mc.textRenderer.getWidth(t) + 2, y + 11, 0x80000000);
            context.drawText(mc.textRenderer, t, x, y, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }
}
