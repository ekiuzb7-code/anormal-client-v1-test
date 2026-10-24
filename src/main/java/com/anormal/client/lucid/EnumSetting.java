package com.anormal.client.lucid;

import com.anormal.client.setting.ModeSetting;

/** Lucid-style enum setting backed by {@link ModeSetting}. */
public class EnumSetting extends ModeSetting {
    public EnumSetting(String name, String description, String[] options, String defaultValue) {
        super(name, description, defaultValue, options);
    }

    public EnumSetting(String name, String description, java.util.List<String> options, String defaultValue) {
        super(name, description, defaultValue, options.toArray(new String[0]));
    }

    public String get() {
        return getValue();
    }

    public int getSelectedIndex() {
        String value = getValue();
        for (int i = 0; i < getModes().size(); i++) {
            if (getModes().get(i).equals(value)) return i;
        }
        return 0;
    }
}
