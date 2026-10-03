package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public class EnemyArmor extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 500.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 340.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showDurability = new BooleanSetting("Show Durability", "Show durability percent", true);
    public final BooleanSetting showEnchants = new BooleanSetting("Show Enchants", "Mark enchanted pieces", true);
    public final BooleanSetting sticky = new BooleanSetting("Sticky", "Keep last target 5s", true);

    private LivingEntity remembered = null;
    private long rememberedAt = 0;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public EnemyArmor() {
        super("EnemyArmor", "Shows target armor and durability", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showDurability);
        addSetting(showEnchants);
        addSetting(sticky);
    }

    private LivingEntity target() {
        try {
            if (mc.targetedEntity instanceof LivingEntity living && living.isAlive() && living != mc.player) {
                remembered = living;
                rememberedAt = System.currentTimeMillis();
                return living;
            }
        } catch (Throwable ignored) {}
        if (sticky.isEnabled() && remembered != null && remembered.isAlive()
                && System.currentTimeMillis() - rememberedAt < 5000) {
            return remembered;
        }
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        LivingEntity target;
        try {
            target = target();
        } catch (Throwable ignored) {
            return;
        }
        if (target == null) return;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        int w = 120;
        int h = 20 + SLOTS.length * 18;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, "Armor: " + target.getName().getString(),
                x + 6, y + 5, 0xFFFFFFFF, true);

        int ly = y + 20;
        for (EquipmentSlot slot : SLOTS) {
            try {
                ItemStack stack = target.getEquippedStack(slot);
                if (stack.isEmpty()) {
                    RenderUtils.drawText(context, mc.textRenderer, "- empty -", x + 24, ly + 4, 0xFF777777, true);
                } else {
                    context.drawItem(stack, x + 4, ly);
                    String label = "";
                    if (showDurability.isEnabled() && stack.isDamageable()) {
                        int max = stack.getMaxDamage();
                        int pct = (int) (((double) (max - stack.getDamage()) / Math.max(1, max)) * 100);
                        label += pct + "%";
                    }
                    boolean ench = false;
                    try {
                        ench = stack.hasEnchantments();
                    } catch (Throwable ignored) {}
                    if (showEnchants.isEnabled() && ench) {
                        if (!label.isEmpty()) label += " ";
                        label += "§d✦";
                    }
                    if (!label.isEmpty()) {
                        RenderUtils.drawText(context, mc.textRenderer, label, x + 26, ly + 4, 0xFFFFFFFF, true);
                    }
                }
            } catch (Throwable ignored) {}
            ly += 18;
        }
    }
}
