package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.Theme;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;

public class ClientSettings extends Module {
    public final ModeSetting themeSetting = new ModeSetting("Theme", "Active GUI Design Style", "Vape V4", "Vape V4", "Glassmorphism");
    public final ColorSetting accentColor = new ColorSetting("Accent Color", "Accent Color for menus and HUD", ColorUtils.rgba(235, 100, 30, 255));
    public final BooleanSetting blurBackground = new BooleanSetting("Blur Background", "Blurs the game world behind the GUI", true);
    public final BooleanSetting showTooltips = new BooleanSetting("Tooltips", "Shows descriptions when hovering over settings", true);
    public final NumberSetting guiScale = new NumberSetting("GUI Scale", "Scale multiplier for GUI elements", 1.0, 0.7, 1.5, 0.1);

    public ClientSettings() {
        super("ClientSettings", "Configure GUI themes, appearance and global options", Category.CLIENT);
        addSetting(themeSetting);
        addSetting(accentColor);
        addSetting(blurBackground);
        addSetting(showTooltips);
        addSetting(guiScale);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (themeSetting.is("Vape V4")) {
            ThemeManager.setActiveTheme(Theme.VAPE_V4);
            ThemeManager.setCustomAccentColor(accentColor.getValue());
        } else {
            ThemeManager.setActiveTheme(Theme.GLASSMORPHISM);
        }
    }
}
