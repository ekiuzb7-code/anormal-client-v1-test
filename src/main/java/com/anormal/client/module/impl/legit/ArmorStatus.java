package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class ArmorStatus extends Module {
    public ArmorStatus() {
        super("ArmorStatus", "Displays currently equipped armor durability and items on HUD", Category.LEGIT);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int x = screenWidth / 2 + 95;
        int y = screenHeight - 60;

        for (int i = 3; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getArmorStack(i);
            if (!stack.isEmpty()) {
                context.drawItem(stack, x, y);
                if (stack.isDamageable()) {
                    int maxDamage = stack.getMaxDamage();
                    int currentDamage = maxDamage - stack.getDamage();
                    int percent = (int) ((currentDamage / (float) maxDamage) * 100);

                    int textColor = percent > 50 ? 0xFF55FF55 : (percent > 20 ? 0xFFFFAA00 : 0xFFFF5555);
                    RenderUtils.drawText(context, mc.textRenderer, percent + "%", x + 20, y + 4, textColor, true);
                }
                y += 18;
            }
        }
    }
}
