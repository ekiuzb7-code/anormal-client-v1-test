package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

public class TargetInfo extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 500.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Target info scale", 1.0, 0.5, 2.0, 0.1);

    public TargetInfo() {
        super("TargetInfo", "Displays target player health, name and armor on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);

    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        if (mc.targetedEntity instanceof LivingEntity target && target.isAlive()) {
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int width = (int) (120 * scale.getValue());
            int height = (int) (36 * scale.getValue());

            RenderUtils.fill(context, x, y, x + width, y + height, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x, y, x + width, y + height, 1, ThemeManager.getBorderColor());

            String name = com.anormal.client.module.impl.client.NameProtect.replaceName(target);
            RenderUtils.drawText(context, mc.textRenderer, name, x + 6, y + 5, 0xFFFFFFFF, true);

            // Health bar
            float maxHp = target.getMaxHealth();
            float hp = target.getHealth();
            float percent = Math.max(0, Math.min(1, hp / maxHp));

            int barWidth = width - 12;
            int barHeight = (int) (8 * scale.getValue());
            int barY = y + height - barHeight - 8;

            RenderUtils.fill(context, x + 6, barY, x + 6 + barWidth, barY + barHeight, 0xFF333333);
            RenderUtils.fill(context, x + 6, barY, x + 6 + (int) (barWidth * percent), barY + barHeight, ThemeManager.getAccentColor());

            String hpText = String.format("%.1f HP", hp);
            RenderUtils.drawText(context, mc.textRenderer, hpText, x + width - mc.textRenderer.getWidth(hpText) - 6, y + 5, ThemeManager.getAccentColor(), true);
        }
    }
}
