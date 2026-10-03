package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class InventoryOverlay extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 1750.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 50.0, 0.0, 1080.0, 1.0);

    public InventoryOverlay() {
        super("InventoryOverlay", "Renders your inventory contents on the HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

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
