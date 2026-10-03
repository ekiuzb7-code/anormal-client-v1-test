package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Health extends Module {
    public final BooleanSetting background = new BooleanSetting("Background", "Renders a pill background behind the text", true);
    public final BooleanSetting absorption = new BooleanSetting("Absorption", "Shows absorption hearts separately", true);

    public Health() {
        super("Health", "Renders health indicators next to crosshair", Category.UZNY11);
        addSetting(background);
        addSetting(absorption);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();
            int hearts = (int) Math.ceil(mc.player.getHealth());
            String hpText = "\u2764 " + hearts;
            float absorb = 0;
            try {
                absorb = mc.player.getAbsorptionAmount();
            } catch (Throwable ignored) {}
            if (absorption.isEnabled() && absorb > 0.5f) hpText += " +" + (int) absorb;
            int w = mc.textRenderer.getWidth(hpText);
            int x = (screenWidth - w) / 2 + 20;
            int y = screenHeight / 2 - 4;
            if (background.isEnabled()) {
                context.fill(x - 3, y - 2, x + w + 3, y + 11, 0xAA000000);
                context.fill(x - 3, y + 10, x + w + 3, y + 11, 0xFFFF4444);
            }
            int col = hearts <= 6 ? 0xFFFF2222 : hearts <= 12 ? 0xFFFFAA22 : 0xFFFF4444;
            RenderUtils.drawText(context, mc.textRenderer, hpText, x, y, col, true);
        } catch (Throwable ignored) {}
    }
}
