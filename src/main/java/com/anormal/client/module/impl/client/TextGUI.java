package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;

public class TextGUI extends Module {
    public final ModeSetting sort = new ModeSetting("Sort", "Sorting method", "Length", "Length", "Alphabetical");
    public final ModeSetting colorMode = new ModeSetting("Color", "Color Mode", "Theme", "Theme", "Rainbow", "Custom");
    public final ColorSetting customColor = new ColorSetting("Custom Color", "Color when in custom mode", ColorUtils.rgba(255, 100, 30, 255));
    public final BooleanSetting background = new BooleanSetting("Background", "Draw dark background behind text", true);
    public final BooleanSetting watermark = new BooleanSetting("Watermark", "Show client brand header", true);

    public TextGUI() {
        super("TextGUI", "Displays active modules on the HUD screen", Category.CLIENT);
        addSetting(sort);
        addSetting(colorMode);
        addSetting(customColor);
        addSetting(background);
        addSetting(watermark);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int y = 4;

        if (watermark.isEnabled()) {
            String clientTitle = "ANORMAL CLIENT";
            String versionText = " v1.0 [1.21.1]";
            int titleWidth = mc.textRenderer.getWidth(clientTitle + versionText);

            if (background.isEnabled()) {
                RenderUtils.fill(context, 4, 4, 10 + titleWidth, 18, ThemeManager.getBackgroundColor());
                RenderUtils.drawBorder(context, 4, 4, 10 + titleWidth, 18, 1, ThemeManager.getBorderColor());
                RenderUtils.fill(context, 4, 4, 6, 18, ThemeManager.getAccentColor());
            }

            RenderUtils.drawText(context, mc.textRenderer, clientTitle, 9, 7, ThemeManager.getAccentColor(), true);
            RenderUtils.drawText(context, mc.textRenderer, versionText, 9 + mc.textRenderer.getWidth(clientTitle), 7, 0xFFAAAAAA, true);
        }

        List<Module> activeModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .filter(m -> !(m instanceof ClientSettings))
                .sorted(sort.is("Length") ?
                        Comparator.comparingInt((Module m) -> mc.textRenderer.getWidth(m.getName())).reversed() :
                        Comparator.comparing(Module::getName))
                .toList();

        int moduleIndex = 0;
        for (Module module : activeModules) {
            String name = module.getName();
            int textWidth = mc.textRenderer.getWidth(name);
            int x = screenWidth - textWidth - 8;

            int color;
            if (colorMode.is("Rainbow")) {
                color = ColorUtils.rainbow(moduleIndex * 150, 0.8f, 1.0f);
            } else if (colorMode.is("Custom")) {
                color = customColor.getValue();
            } else {
                color = ThemeManager.getAccentColor();
            }

            if (background.isEnabled()) {
                RenderUtils.fill(context, x - 4, y, screenWidth - 2, y + 12, ThemeManager.getBackgroundColor());
                RenderUtils.fill(context, screenWidth - 4, y, screenWidth - 2, y + 12, color);
            }

            RenderUtils.drawText(context, mc.textRenderer, name, x, y + 2, color, true);
            y += 13;
            moduleIndex++;
        }
    }
}
