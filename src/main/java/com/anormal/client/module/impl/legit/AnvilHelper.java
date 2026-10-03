package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;

public class AnvilHelper extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 240.0, 0.0, 1080.0, 1.0);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Anvil text color", ColorUtils.rgba(255, 255, 255, 255));

    public AnvilHelper() {
        super("AnvilHelper", "Shows anvil XP cost for held item", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            if (!(mc.player.currentScreenHandler instanceof AnvilScreenHandler)) return;
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return;
            String text;
            try {
                Integer cost = stack.get(DataComponentTypes.REPAIR_COST);
                text = stack.getName().getString() + " cost: " + (cost == null ? 0 : cost) + " XP";
            } catch (Throwable t) {
                text = stack.getName().getString() + " (cost unknown)";
            }
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
