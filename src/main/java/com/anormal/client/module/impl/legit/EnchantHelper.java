package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EnchantHelper extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 210.0, 0.0, 1080.0, 1.0);
    public final NumberSetting maxLines = new NumberSetting("Max Lines", "Max enchant lines shown", 4.0, 1.0, 8.0, 1.0);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Enchant text color", ColorUtils.rgba(255, 255, 255, 255));

    public EnchantHelper() {
        super("EnchantHelper", "Lists enchantments on held item", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(maxLines);
        addSetting(textColor);
    }

    private String roman(int n) {
        if (n >= 10) return "X";
        if (n == 9) return "IX";
        if (n >= 5) return "V" + roman(n - 5);
        if (n == 4) return "IV";
        if (n >= 1) return "I" + roman(n - 1);
        return "";
    }

    private List<String> lines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        try {
            ItemEnchantmentsComponent comp = stack.getEnchantments();
            if (comp == null || comp.isEmpty()) return out;
            for (net.minecraft.registry.entry.RegistryEntry<Enchantment> e : comp.getEnchantments()) {
                try {
                    String path = e.getKey().map(k -> k.getValue().getPath().replace('_', ' ')).orElse("enchantment");
                    int lvl = comp.getLevel(e);
                    out.add(path.substring(0, 1).toUpperCase() + path.substring(1) + (lvl > 0 ? " " + roman(lvl) : ""));
                } catch (Throwable ignored) {}
                if (out.size() >= maxLines.getValue().intValue()) break;
            }
        } catch (Throwable ignored) {}
        return out;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return;
            List<String> rows = lines(stack);
            if (rows.isEmpty()) rows.add("No enchantments");
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            for (String row : rows) {
                int w = mc.textRenderer.getWidth(row);
                RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
                RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
                RenderUtils.drawText(context, mc.textRenderer, row, x, y, textColor.getValue(), true);
                y += 16;
            }
        } catch (Throwable ignored) {}
    }
}
