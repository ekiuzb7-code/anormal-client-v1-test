package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorStatus extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 1820.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 50.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showDamage = new BooleanSetting("Damage %", "Displays durability percent", true);
    public final BooleanSetting showItemCount = new BooleanSetting("Item Count", "Displays stack count", false);

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public ArmorStatus() {
        super("ArmorStatus", "Displays equipped armor icons and durability on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showDamage);
        addSetting(showItemCount);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        int startX = posX.getValue().intValue();
        int startY = posY.getValue().intValue();
        int yOffset = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                context.drawItem(stack, startX, startY + yOffset);

                if (showDamage.isEnabled() && stack.isDamageable()) {
                    int maxDamage = stack.getMaxDamage();
                    int currentDamage = stack.getDamage();
                    int percent = (int) (((double) (maxDamage - currentDamage) / maxDamage) * 100);
                    int color = percent > 50 ? 0xFF55FF55 : (percent > 25 ? 0xFFFFFF55 : 0xFFFF5555);
                    RenderUtils.drawText(context, mc.textRenderer, percent + "%", startX - 24, startY + yOffset + 4, color, true);
                }
                yOffset += 18;
            }
        }
    }
}
