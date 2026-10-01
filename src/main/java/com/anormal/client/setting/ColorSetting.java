package com.anormal.client.setting;

public class ColorSetting extends Setting<Integer> {
    private boolean rainbow;

    public ColorSetting(String name, String description, int defaultColorHex, boolean rainbow) {
        super(name, description, defaultColorHex);
        this.rainbow = rainbow;
    }

    public ColorSetting(String name, String description, int defaultColorHex) {
        this(name, description, defaultColorHex, false);
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public int getRed() {
        return (getValue() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (getValue() >> 8) & 0xFF;
    }

    public int getBlue() {
        return getValue() & 0xFF;
    }

    public int getAlpha() {
        return (getValue() >> 24) & 0xFF;
    }
}
