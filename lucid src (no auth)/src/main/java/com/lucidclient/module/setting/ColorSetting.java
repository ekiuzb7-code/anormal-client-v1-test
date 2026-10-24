package com.lucidclient.module.setting;

/**
 * A color setting rendered as a swatch + hex value (click to cycle presets).
 * Stored as an ARGB int.
 */
public class ColorSetting extends Setting<Integer> {
    // Preset palette — matches the ColorPicker component presets.
    private static final int[] PRESETS = {
        0xFF111111, 0xFFFFFFFF, 0xFFE6E6E6, 0xFF666666,
        0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF3B82F6,
        0xFFEC4899, 0xFF8B5CF6, 0xFF06B6D4, 0xFFF97316
    };
    private int presetIndex = 0;

    public ColorSetting(String name, String description, int defaultColor) {
        super(name, description, defaultColor);
        // Find the preset index for the default
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == defaultColor) { presetIndex = i; break; }
        }
    }

    public int[] getPresets() { return PRESETS; }

    /** Cycle to the next preset color. */
    public void cycleNext() {
        presetIndex = (presetIndex + 1) % PRESETS.length;
        value = PRESETS[presetIndex];
    }

    public void setColor(int argb) {
        value = argb;
        // Find preset index if it matches
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == argb) { presetIndex = i; return; }
        }
    }

    public int getColor() { return value; }

    /** Hex string like "#FF111111" (with alpha) or "#111111" (without). */
    public String getHex() {
        return String.format("#%08X", value);
    }

    @Override public int getControlHeight() { return 28; }
    @Override public String getType() { return "color"; }
}
