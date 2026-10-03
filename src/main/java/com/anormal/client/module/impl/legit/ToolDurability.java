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
import net.minecraft.item.ItemStack;

public class ToolDurability extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 60.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showBar = new BooleanSetting("Show Bar", "Draw durability bar under text", true);
    public final BooleanSetting showPercent = new BooleanSetting("Show Percent", "Append durability percent", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Durability text color", ColorUtils.rgba(255, 255, 255, 255));

    public ToolDurability() {
        super("ToolDurability", "Shows held tool durability number and bar", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showBar);
        addSetting(showPercent);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty() || !stack.isDamageable()) return;
            int max = stack.getMaxDamage();
            int left = Math.max(0, max - stack.getDamage());
            int pct = max > 0 ? (int) ((double) left / max * 100.0) : 100;
            String text = "Tool: " + left + "/" + max + (showPercent.isEnabled() ? " (" + pct + "%)" : "");
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
            if (showBar.isEnabled()) {
                int col = pct > 50 ? 0xFF55FF55 : (pct > 25 ? 0xFFFFFF55 : 0xFFFF5555);
                context.fill(x - 4, y + 12, x + w + 4, y + 14, 0xFF222222);
                int bw = (int) ((w + 8) * (pct / 100.0));
                context.fill(x - 4, y + 12, x - 4 + bw, y + 14, col);
            }
        } catch (Throwable ignored) {}
    }
}
