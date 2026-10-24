package com.anormal.client.lucid;

import com.anormal.client.setting.NumberSetting;

/** Lucid-style int setting backed by {@link NumberSetting} so the GUI works unchanged. */
public class IntSetting extends NumberSetting {
    public IntSetting(String name, String description, int min, int max, int defaultValue, int step) {
        super(name, description, defaultValue, min, max, Math.max(1, step));
    }

    public int get() {
        return getValue().intValue();
    }

    public void set(int value) {
        setValue((double) value);
    }
}
