package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

public class TargetInfo extends Module {
    public TargetInfo() {
        super("TargetInfo", "Displays target player health, name and armor on HUD", Category.LEGIT);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        if (mc.targetedEntity instanceof LivingEntity target && target.isAlive()) {
            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();

            int x = screenWidth / 2 + 10;
            int y = screenHeight / 2 + 25;
            int width = 120;
            int height = 36;

            RenderUtils.fill(context, x, y, x + width, y + height, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x, y, x + width, y + height, 1, ThemeManager.getBorderColor());

            String name = target.getName().getString();
            context.drawTextWithShadow(mc.textRenderer, name, x + 6, y + 5, 0xFFFFFFFF);

            // Health bar
            float maxHp = target.getMaxHealth();
            float hp = target.getHealth();
            float percent = Math.max(0, Math.min(1, hp / maxHp));

            int barWidth = width - 12;
            int barHeight = 8;
            int barY = y + 18;

            RenderUtils.fill(context, x + 6, barY, x + 6 + barWidth, barY + barHeight, 0xFF333333);
            RenderUtils.fill(context, x + 6, barY, x + 6 + (int) (barWidth * percent), barY + barHeight, ThemeManager.getAccentColor());

            String hpText = String.format("%.1f HP", hp);
            context.drawTextWithShadow(mc.textRenderer, hpText, x + width - mc.textRenderer.getWidth(hpText) - 6, y + 5, ThemeManager.getAccentColor());
        }
    }
}
