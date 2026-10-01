package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;

import java.util.Collection;

public class PotionStatus extends Module {
    public PotionStatus() {
        super("PotionStatus", "Displays active potion effects and remaining durations on HUD", Category.LEGIT);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        if (effects.isEmpty()) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int y = screenHeight - 20 - (effects.size() * 14);

        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            String duration = StatusEffectUtil.getDurationText(effect, 1.0f, 20.0f).getString();
            String text = name + " " + duration;

            int width = mc.textRenderer.getWidth(text);
            int x = screenWidth - width - 8;

            RenderUtils.fill(context, x - 4, y, screenWidth - 2, y + 12, ThemeManager.getBackgroundColor());
            RenderUtils.fill(context, screenWidth - 4, y, screenWidth - 2, y + 12, ThemeManager.getAccentColor());

            RenderUtils.drawText(context, mc.textRenderer, text, x, y + 2, 0xFFFFFFFF, true);
            y += 14;
        }
    }
}
