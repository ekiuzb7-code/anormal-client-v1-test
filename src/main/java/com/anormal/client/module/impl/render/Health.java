package com.anormal.client.module.impl.render;
import com.anormal.client.util.RenderUtils;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.client.gui.DrawContext;

public class Health extends Module {
    public Health() {
        super("Health", "Renders health indicators next to crosshair", Category.RENDER);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int hearts = (int) Math.ceil(mc.player.getHealth());
        String hpText = "❤ " + hearts;
        int w = mc.textRenderer.getWidth(hpText);

        RenderUtils.drawText(context, mc.textRenderer, hpText, (screenWidth - w) / 2 + 20, screenHeight / 2 - 4, 0xFFFF4444, true);
    }
}
