package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class EnemyInventory extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 640.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 340.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showMainHand = new BooleanSetting("Main Hand", "Show held item", true);
    public final BooleanSetting showOffHand = new BooleanSetting("Off Hand", "Show offhand item", true);
    public final BooleanSetting showArmor = new BooleanSetting("Show Armor", "Show equipped armor row", true);
    public final BooleanSetting sticky = new BooleanSetting("Sticky", "Keep last target 5s", true);

    private LivingEntity remembered = null;
    private long rememberedAt = 0;

    public EnemyInventory() {
        super("EnemyInventory", "Shows target visible equipment", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showMainHand);
        addSetting(showOffHand);
        addSetting(showArmor);
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
        // Only equipped items are visible client-side (hands + armor)
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        int rows = 0;
        if (showMainHand.isEnabled()) rows++;
        if (showOffHand.isEnabled()) rows++;
        if (showArmor.isEnabled()) rows++;
        if (rows == 0) return;
        int w = 150;
        int h = 20 + rows * 18;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, "Inv: " + target.getName().getString(),
                x + 6, y + 5, 0xFFFFFFFF, true);

        int ly = y + 20;
        if (showMainHand.isEnabled()) {
            drawStack(context, target.getMainHandStack(), "Main", x + 4, ly);
            ly += 18;
        }
        if (showOffHand.isEnabled()) {
            drawStack(context, target.getOffHandStack(), "Off", x + 4, ly);
            ly += 18;
        }
        if (showArmor.isEnabled() && target instanceof PlayerEntity p) {
            try {
                ItemStack chest = p.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
                drawStack(context, chest, "Body", x + 4, ly);
            } catch (Throwable ignored) {}
        }
    }

    private void drawStack(DrawContext context, ItemStack stack, String tag, int x, int y) {
        try {
            if (stack == null || stack.isEmpty()) {
                RenderUtils.drawText(context, mc.textRenderer, tag + ": —", x + 22, y + 4, 0xFF777777, true);
                return;
            }
            context.drawItem(stack, x, y);
            String label = tag + ": " + stack.getName().getString();
            if (stack.getCount() > 1) label += " x" + stack.getCount();
            boolean ench = false;
            try {
                ench = stack.hasEnchantments();
            } catch (Throwable ignored) {}
            if (ench) label += " §d✦";
            RenderUtils.drawText(context, mc.textRenderer, label, x + 22, y + 4, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }
}
