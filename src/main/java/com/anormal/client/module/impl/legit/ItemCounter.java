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
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class ItemCounter extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 150.0, 0.0, 1080.0, 1.0);
    public final ModeSetting itemMode = new ModeSetting("Item Mode", "Which item to count", "Held", "Held", "EnderPearl", "Totem", "GoldenApple");
    public final ColorSetting textColor = new ColorSetting("Text Color", "Counter text color", ColorUtils.rgba(255, 255, 255, 255));

    public ItemCounter() {
        super("ItemCounter", "Counts held item type across inventory", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(itemMode);
        addSetting(textColor);
    }

    private boolean matches(String path) {
        try {
            if (itemMode.is("EnderPearl")) return path.equals("ender_pearl");
            if (itemMode.is("Totem")) return path.equals("totem_of_undying");
            if (itemMode.is("GoldenApple")) return path.contains("golden_apple");
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            String label = itemMode.getValue();
            String heldPath = "";
            try {
                heldPath = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath();
            } catch (Throwable ignored) {}
            if (itemMode.is("Held")) {
                if (heldPath.isEmpty() || mc.player.getMainHandStack().isEmpty()) return;
                label = mc.player.getMainHandStack().getName().getString();
            }
            int total = 0;
            for (int i = 0; i < 36; i++) {
                try {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s.isEmpty()) continue;
                    String p = Registries.ITEM.getId(s.getItem()).getPath();
                    if (itemMode.is("Held") ? p.equals(heldPath) : matches(p)) total += s.getCount();
                } catch (Throwable ignored) {}
            }
            try {
                ItemStack off = mc.player.getOffHandStack();
                if (!off.isEmpty()) {
                    String p = Registries.ITEM.getId(off.getItem()).getPath();
                    if (itemMode.is("Held") ? p.equals(heldPath) : matches(p)) total += off.getCount();
                }
            } catch (Throwable ignored) {}
            String text = label + ": " + total;
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
