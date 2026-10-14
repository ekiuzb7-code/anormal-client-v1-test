package com.anormal.client.theme;

import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class ThemeManager {
    private static Theme activeTheme = Theme.VAPE_V4;
    private static int customAccentColor = ColorUtils.rgba(255, 85, 0, 255); // vivid brutalist orange

    public static Theme getActiveTheme() {
        return activeTheme;
    }

    public static void setActiveTheme(Theme theme) {
        activeTheme = theme;
    }

    public static int getAccentColor() {
        if (activeTheme == Theme.VAPE_V4 || activeTheme == Theme.BRUTALISM) {
            return customAccentColor;
        } else {
            return ColorUtils.rgba(70, 160, 245, 255); // Frost glass neon blue
        }
    }

    public static void setCustomAccentColor(int color) {
        customAccentColor = color;
    }

    public static int getBackgroundColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return ColorUtils.rgba(17, 19, 26, 255); // Deep neo-brutalist graphite
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(20, 20, 20, 245);
        } else {
            return ColorUtils.rgba(18, 22, 34, 165); // Frosted Glass
        }
    }

    public static int getHeaderColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return ColorUtils.rgba(0, 0, 0, 255); // Stark black
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(15, 15, 15, 250);
        } else {
            return ColorUtils.rgba(25, 32, 50, 190);
        }
    }

    public static int getCardColor(boolean enabled, boolean hovered) {
        if (activeTheme == Theme.BRUTALISM) {
            if (enabled) {
                return hovered ? ColorUtils.rgba(36, 42, 58, 255) : ColorUtils.rgba(24, 28, 38, 255);
            } else {
                return hovered ? ColorUtils.rgba(25, 29, 39, 255) : ColorUtils.rgba(18, 20, 28, 255);
            }
        } else if (activeTheme == Theme.VAPE_V4) {
            if (enabled) {
                return hovered ? ColorUtils.rgba(38, 38, 38, 255) : ColorUtils.rgba(30, 30, 30, 255);
            } else {
                return hovered ? ColorUtils.rgba(24, 24, 24, 240) : ColorUtils.rgba(18, 18, 18, 240);
            }
        } else {
            // Glassmorphism
            if (enabled) {
                return hovered ? ColorUtils.rgba(45, 60, 95, 180) : ColorUtils.rgba(35, 48, 80, 150);
            } else {
                return hovered ? ColorUtils.rgba(30, 38, 55, 130) : ColorUtils.rgba(22, 28, 42, 100);
            }
        }
    }

    public static int getBorderColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return 0xFFFFFFFF; // Stark solid white border
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(10, 10, 10, 255);
        } else {
            return ColorUtils.rgba(255, 255, 255, 45);
        }
    }

    public static int getGlowColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return customAccentColor;
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(0, 0, 0, 100);
        } else {
            return ColorUtils.rgba(80, 140, 230, 70);
        }
    }

    public static int getTextColor(boolean active) {
        if (active) {
            return 0xFFFFFFFF;
        } else {
            return activeTheme == Theme.BRUTALISM ? 0xFF8B95AF : (activeTheme == Theme.VAPE_V4 ? 0xFFAAAAAA : 0xFFB0BCCC);
        }
    }

    public static void renderWindow(DrawContext context, int x, int y, int width, int height, String title) {
        if (activeTheme == Theme.BRUTALISM) {
            // Hard offset brutalist drop shadow (box-shadow: 6px 6px 0px accent)
            RenderUtils.fill(context, x + 6, y + 6, x + width + 6, y + height + 6, getAccentColor());
            // Main container
            RenderUtils.fill(context, x, y, x + width, y + height, getBackgroundColor());
            RenderUtils.drawBorder(context, x, y, x + width, y + height, 2, 0xFFFFFFFF);
            // Black header bar
            RenderUtils.fill(context, x + 2, y + 2, x + width - 2, y + 26, 0xFF000000);
            // White divider line
            RenderUtils.fill(context, x, y + 25, x + width, y + 27, 0xFFFFFFFF);
        } else if (activeTheme == Theme.VAPE_V4) {
            RenderUtils.drawDarkPanel(context, x, y, x + width, y + height, 22, getHeaderColor(), getBackgroundColor(), getAccentColor());
        } else {
            RenderUtils.drawGlassPanel(context, x, y, x + width, y + height, getBackgroundColor(), getBorderColor(), getGlowColor());
            // Header divider line with glow
            context.fill(x + 2, y + 22, x + width - 2, y + 23, ColorUtils.rgba(100, 180, 255, 80));
        }
    }
}
