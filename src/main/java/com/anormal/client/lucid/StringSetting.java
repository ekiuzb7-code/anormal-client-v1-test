package com.anormal.client.lucid;

import com.anormal.client.setting.Setting;

/** Lucid-style free-text setting. Stored like any other setting. */
public class StringSetting extends Setting<String> {
    private final int maxLength;

    public StringSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, 64);
    }

    public StringSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue == null ? "" : defaultValue);
        this.maxLength = maxLength;
    }

    public String get() {
        return getValue();
    }

    public void set(String value) {
        if (value == null) value = "";
        setValue(value.length() > maxLength ? value.substring(0, maxLength) : value);
    }

    public int getMaxLength() {
        return maxLength;
    }
}
