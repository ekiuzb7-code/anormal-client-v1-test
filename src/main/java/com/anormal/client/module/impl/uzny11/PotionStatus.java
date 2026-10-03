package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;

import java.util.Collection;

public class PotionStatus extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "Right-edge anchor X", 1918.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Top anchor Y", 900.0, 0.0, 1080.0, 1.0);

    public PotionStatus() {
        super("PotionStatus", "Displays active potion effects and remaining durations on HUD", Category.UZNY11);
        addSetting(posX);
        addSetting(posY);

    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        if (effects.isEmpty()) return;

        int rightEdge = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            String duration = StatusEffectUtil.getDurationText(effect, 1.0f, 20.0f).getString();
            String text = name + " " + duration;

            int width = mc.textRenderer.getWidth(text);
            int x = rightEdge - width - 6;

            RenderUtils.fill(context, x - 4, y, rightEdge, y + 12, ThemeManager.getBackgroundColor());
            RenderUtils.fill(context, rightEdge - 2, y, rightEdge, y + 12, ThemeManager.getAccentColor());

            RenderUtils.drawText(context, mc.textRenderer, text, x, y + 2, 0xFFFFFFFF, true);
            y += 14;
        }
    }
}
