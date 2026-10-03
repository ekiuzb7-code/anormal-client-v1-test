package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class BlockCounter extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 180.0, 0.0, 1080.0, 1.0);
    public final ModeSetting blockMode = new ModeSetting("Block Mode", "Which blocks to count", "Obsidian", "Obsidian", "All Building");
    public final ColorSetting textColor = new ColorSetting("Text Color", "Counter text color", ColorUtils.rgba(255, 255, 255, 255));

    public BlockCounter() {
        super("BlockCounter", "Counts building blocks in inventory", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(blockMode);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int total = 0;
            for (int i = 0; i < 36; i++) {
                try {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s.isEmpty()) continue;
                    if (blockMode.is("Obsidian")) {
                        String p = Registries.ITEM.getId(s.getItem()).getPath();
                        if (p.equals("obsidian") || p.equals("crying_obsidian")) total += s.getCount();
                    } else if (s.getItem() instanceof BlockItem) {
                        total += s.getCount();
                    }
                } catch (Throwable ignored) {}
            }
            String text = blockMode.getValue() + ": " + total;
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
