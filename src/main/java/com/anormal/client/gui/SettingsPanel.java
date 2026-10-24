package com.anormal.client.gui;

import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.Locale;

/**
 * Compact, scrollable settings list drawn inside a custom screen.
 *
 * <p>The ClickGUI's inline rows cannot be reused here, so this panel renders the same setting types
 * with the same interactions: booleans toggle, modes cycle (right-click cycles back), numbers step
 * with -/+ halves, colours cycle a small palette. Text settings are handled by the host screen's
 * own text fields and are skipped here.</p>
 */
public final class SettingsPanel {

    public static final int ROW_H = 16;

    private static final int TEXT = 0xFFE8E8EC;
    private static final int TEXT_DIM = 0xFF8A8A96;
    private static final int ON = 0xFF6BD96B;
    private static final int OFF = 0xFF7A7A86;
    private static final int ROW_HOVER = 0x18FFFFFF;

    /** Small palette for colour cycling - readable on dark backgrounds. */
    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFFFC800, 0xFFFF5555, 0xFF55FF55,
            0xFF55FFFF, 0xFF9B6BFF, 0xFFFF77CC, 0xFFAAAAAA, 0xFF000000,
    };

    private SettingsPanel() {
    }

    /** Number of rows this list would occupy (text settings are skipped). */
    public static int rowCount(List<Setting<?>> settings) {
        int n = 0;
        for (Setting<?> s : settings) {
            if (s instanceof com.anormal.client.lucid.StringSetting) continue;
            if (s instanceof KeybindSetting) continue;
            if (!s.isVisible()) continue;
            n++;
        }
        return n;
    }

    public static int contentHeight(List<Setting<?>> settings) {
        return rowCount(settings) * ROW_H;
    }

    /**
     * Draws the list.
     *
     * @param scroll pixel offset applied to the first row (host owns clamping)
     */
    public static void render(DrawContext ctx, TextRenderer tr, List<Setting<?>> settings,
                              int x, int y, int w, int viewHeight, int scroll,
                              int mouseX, int mouseY) {
        int rowY = y - scroll;
        for (Setting<?> setting : settings) {
            if (setting instanceof com.anormal.client.lucid.StringSetting) continue;
            if (setting instanceof KeybindSetting) continue;
            if (!setting.isVisible()) continue;

            // Clip rows that are outside the viewport so scrolled content does not bleed out.
            if (rowY + ROW_H < y || rowY > y + viewHeight) {
                rowY += ROW_H;
                continue;
            }

            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (hovered) {
                ctx.fill(x, rowY, x + w, rowY + ROW_H, ROW_HOVER);
            }

            String label = setting.getName();
            ctx.drawText(tr, label, x + 6, rowY + 4, TEXT_DIM, false);

            String value = valueText(setting);
            int valueWidth = tr.getWidth(value);
            int valueX = x + w - valueWidth - 8;
            int valueColor = TEXT;

            if (setting instanceof BooleanSetting bool) {
                valueColor = bool.isEnabled() ? ON : OFF;
            } else if (setting instanceof ColorSetting color) {
                // Swatch before the hex so the colour is readable at a glance.
                ctx.fill(valueX - 12, rowY + 3, valueX - 3, rowY + ROW_H - 3, 0xFF000000 | color.getColor());
                ctx.fill(valueX - 12, rowY + 3, valueX - 3, rowY + 4, 0xFF000000);
                ctx.fill(valueX - 12, rowY + ROW_H - 4, valueX - 3, rowY + ROW_H - 3, 0xFF000000);
            } else if (setting instanceof NumberSetting) {
                // Hint that the halves are clickable.
                ctx.drawText(tr, "-", valueX - 10, rowY + 4, TEXT_DIM, false);
                ctx.drawText(tr, "+", x + w - 6, rowY + 4, TEXT_DIM, false);
            }

            ctx.drawText(tr, value, valueX, rowY + 4, valueColor, false);
            rowY += ROW_H;
        }
    }

    /**
     * Handles a click inside the list.
     *
     * @return true when a setting consumed the click
     */
    public static boolean click(List<Setting<?>> settings, double mx, double my,
                                int x, int y, int w, int viewHeight, int scroll, int button) {
        int rowY = y - scroll;
        for (Setting<?> setting : settings) {
            if (setting instanceof com.anormal.client.lucid.StringSetting) continue;
            if (setting instanceof KeybindSetting) continue;
            if (!setting.isVisible()) continue;

            if (rowY + ROW_H >= y && rowY <= y + viewHeight
                    && mx >= x && mx <= x + w && my >= rowY && my <= rowY + ROW_H) {
                if (setting instanceof BooleanSetting bool) {
                    bool.toggle();
                    return true;
                }
                if (setting instanceof ModeSetting mode) {
                    if (button == 1) mode.cycleBack();
                    else mode.cycle();
                    return true;
                }
                if (setting instanceof NumberSetting number) {
                    double step = number.getStep() <= 0 ? 0.05 : number.getStep();
                    // Left third steps down, right third steps up, the middle does nothing so a
                    // misclick cannot change a value silently.
                    double relative = (mx - x) / (double) w;
                    double value = number.getValue();
                    if (relative < 0.25) {
                        value -= step;
                    } else if (relative > 0.75) {
                        value += step;
                    } else {
                        return true;
                    }
                    value = Math.max(number.getMin(), Math.min(number.getMax(), value));
                    number.setValue(value);
                    return true;
                }
                if (setting instanceof ColorSetting color) {
                    color.setValue(nextColor(color.getColor()));
                    return true;
                }
                return true;
            }
            rowY += ROW_H;
        }
        return false;
    }

    private static int nextColor(int current) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == (current & 0xFFFFFF | 0xFF000000) || PALETTE[i] == current) {
                return PALETTE[(i + 1) % PALETTE.length];
            }
        }
        return PALETTE[0];
    }

    private static String valueText(Setting<?> setting) {
        if (setting instanceof BooleanSetting bool) {
            return bool.isEnabled() ? "ON" : "OFF";
        }
        if (setting instanceof ModeSetting mode) {
            return mode.getValue();
        }
        if (setting instanceof NumberSetting number) {
            double v = number.getValue();
            double step = number.getStep();
            if (step >= 1.0) return String.valueOf((int) v);
            if (step >= 0.1) return String.format(Locale.ROOT, "%.1f", v);
            return String.format(Locale.ROOT, "%.2f", v);
        }
        if (setting instanceof ColorSetting color) {
            return String.format(Locale.ROOT, "#%06X", color.getColor() & 0xFFFFFF);
        }
        return "";
    }
}
