package com.anormal.client.lucid;

import com.anormal.client.setting.NumberSetting;

/** Lucid-style float setting backed by {@link NumberSetting}. */
public class FloatSetting extends NumberSetting {
    public FloatSetting(String name, String description, float min, float max, float defaultValue, int decimals) {
        super(name, description, defaultValue, min, max, decimals <= 0 ? 1.0 : Math.pow(10, -decimals));
    }

    public float get() {
        return getValue().floatValue();
    }

    public void set(float value) {
        setValue((double) value);
    }
}
