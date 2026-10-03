package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorDurability extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 90.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showPercent = new BooleanSetting("Show Percent", "Append durability percent per piece", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Durability text color", ColorUtils.rgba(255, 255, 255, 255));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ArmorDurability() {
        super("ArmorDurability", "Lists armor pieces durability values", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showPercent);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty()) continue;
                String text = stack.getName().getString();
                if (stack.isDamageable()) {
                    int max = stack.getMaxDamage();
                    int left = Math.max(0, max - stack.getDamage());
                    text += ": " + left + "/" + max;
                    if (showPercent.isEnabled()) text += " (" + (max > 0 ? (left * 100 / max) : 100) + "%)";
                }
                int w = mc.textRenderer.getWidth(text);
                RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
                RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
                RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
                y += 16;
            }
        } catch (Throwable ignored) {}
    }
}
