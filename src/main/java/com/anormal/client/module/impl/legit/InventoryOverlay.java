package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class InventoryOverlay extends Module {
    public InventoryOverlay() {
        super("InventoryOverlay", "Renders your inventory contents on the HUD", Category.LEGIT);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        int screenWidth = mc.getWindow().getScaledWidth();
        int x = screenWidth - 170;
        int y = 50;

        RenderUtils.fill(context, x - 2, y - 2, x + 164, y + 60, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x - 2, y - 2, x + 164, y + 60, 1, ThemeManager.getBorderColor());

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, x + col * 18, y + row * 18);
                    if (stack.getCount() > 1 && mc.textRenderer != null) {
                        String count = String.valueOf(stack.getCount());
                        RenderUtils.drawText(context, mc.textRenderer, count, x + col * 18 + 19 - mc.textRenderer.getWidth(count), y + row * 18 + 9, 0xFFFFFFFF, true);
                    }
                }
            }
        }
    }
}
